package mcport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Sign-in with a Microsoft account that owns Minecraft: Java Edition
 * (device code flow -> Xbox Live -> XSTS -> Minecraft services), and
 * joining online-mode servers with it.
 *
 * Needs an Azure application (client ID) that Mojang has allowed to use the
 * Minecraft API; it is set when the APK is built (assets/auth.txt).
 * Tokens stay in this class and the app's private preferences; the game only
 * ever sees the player's name.
 */
final class Auth {
    private Auth() {}

    private static final String MS = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    private static final String SCOPE = "XboxLive.signin offline_access";

    static final class DeviceCode {
        String deviceCode, userCode, url, message;
        int interval, expiresIn;
    }

    /** Thrown with a message meant for the player (Traditional Chinese + English). */
    static class AuthException extends Exception {
        AuthException(String m) { super(m); }
    }

    static final class NoClientId extends Exception {}

    /** The entered client ID was refused by Microsoft; message for the player. */
    static final class BadClientId extends AuthException {
        BadClientId(String m) { super(m); }
    }

    private static String clientId;
    private static SharedPreferences prefs;
    // signed-in account (null name = not signed in)
    private static volatile String name, uuid, mcToken;
    private static volatile long mcTokenExpires;

    static synchronized void init(Context c) {
        if (prefs != null) return;
        prefs = c.getSharedPreferences("mcport-auth", Context.MODE_PRIVATE);
        try {
            InputStream in = c.getAssets().open("auth.txt");
            String id = new String(readAll(in), "UTF-8").trim();
            in.close();
            if (id.length() > 0) clientId = id;
        } catch (IOException ignored) {}
        String own = c.getSharedPreferences("mcport-auth-id", Context.MODE_PRIVATE).getString("client_id", null);
        if (clientId == null && own != null && own.length() > 0) clientId = own;
        name = prefs.getString("name", null);
        uuid = prefs.getString("uuid", null);
    }

    /** The client ID entered in the app (kept apart from sign-in data, so sign-out keeps it). */
    static void setClientId(Context c, String id) {
        id = id == null ? "" : id.trim();
        c.getSharedPreferences("mcport-auth-id", Context.MODE_PRIVATE).edit().putString("client_id", id).apply();
        clientId = id.length() > 0 ? id : null;
    }

    static String clientId() { return clientId; }

    /** True if this APK was built with a client ID, so signing in is possible at all. */
    static boolean available() { return clientId != null; }

    static boolean signedIn() { return name != null && prefs.getString("refresh", null) != null; }

    static String name() { return name; }

    static void signOut() {
        name = uuid = mcToken = null;
        prefs.edit().clear().apply();
    }

    // ---- device code flow ----

    static DeviceCode start() throws Exception {
        if (!available()) throw new NoClientId();
        JSONObject r;
        try {
            r = postForm(MS + "devicecode", "client_id=" + enc(clientId) + "&scope=" + enc(SCOPE));
        } catch (HttpError e) {
            String d = e.json != null ? e.json.optString("error_description") : "";
            if (d.contains("AADSTS700016") || d.contains("AADSTS90002"))
                throw new BadClientId("找不到這個應用程式 ID，請檢查有沒有貼錯，以及帳戶類型是否包含個人 Microsoft 帳戶。");
            if (d.contains("AADSTS7000218") || d.contains("AADSTS70002") || (e.json != null && e.json.optString("error").equals("unauthorized_client")))
                throw new BadClientId("這個應用程式沒有開啟「允許公用用戶端流程」：到 Azure 的「驗證」頁設為「是」。");
            if (d.contains("AADSTS50059") || d.contains("AADSTS9002331"))
                throw new BadClientId("應用程式的帳戶類型要包含「個人 Microsoft 帳戶」。");
            throw e;
        }
        DeviceCode d = new DeviceCode();
        d.deviceCode = r.getString("device_code");
        d.userCode = r.getString("user_code");
        d.url = r.optString("verification_uri", "https://www.microsoft.com/link");
        d.message = r.optString("message");
        d.interval = Math.max(1, r.optInt("interval", 5));
        d.expiresIn = r.optInt("expires_in", 900);
        return d;
    }

    /**
     * Wait for the player to enter the code, then finish the sign-in.
     * Returns the Minecraft name. Checks `cancelled` between polls.
     */
    static String finish(DeviceCode d, boolean[] cancelled) throws Exception {
        long end = System.currentTimeMillis() + d.expiresIn * 1000L;
        int interval = d.interval;
        while (System.currentTimeMillis() < end) {
            Thread.sleep(interval * 1000L);
            if (cancelled[0]) throw new AuthException("已取消 Cancelled");
            JSONObject r;
            try {
                r = postForm(MS + "token", "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
                        + "&client_id=" + enc(clientId) + "&device_code=" + enc(d.deviceCode));
            } catch (HttpError e) {
                String err = e.json != null ? e.json.optString("error") : "";
                if (err.equals("authorization_pending")) continue;
                if (err.equals("slow_down")) { interval += 5; continue; }
                if (err.equals("authorization_declined")) throw new AuthException("登入被拒絕 Sign-in was declined");
                if (err.equals("expired_token")) break;
                throw e;
            }
            return minecraftFromMicrosoft(r);
        }
        throw new AuthException("代碼已過期，請再試一次 The code expired, try again");
    }

    /** Microsoft token response -> Xbox -> Minecraft; stores the account. */
    private static String minecraftFromMicrosoft(JSONObject ms) throws Exception {
        String msToken = ms.getString("access_token");
        String refresh = ms.optString("refresh_token", null);

        JSONObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", new JSONObject()
                .put("Properties", new JSONObject().put("AuthMethod", "RPS").put("SiteName", "user.auth.xboxlive.com").put("RpsTicket", "d=" + msToken))
                .put("RelyingParty", "http://auth.xboxlive.com").put("TokenType", "JWT"), null);
        String uhs = xbl.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs");

        JSONObject xsts;
        try {
            xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", new JSONObject()
                    .put("Properties", new JSONObject().put("SandboxId", "RETAIL").put("UserTokens", new JSONArray().put(xbl.getString("Token"))))
                    .put("RelyingParty", "rp://api.minecraftservices.com/").put("TokenType", "JWT"), null);
        } catch (HttpError e) {
            long x = e.json != null ? e.json.optLong("XErr") : 0;
            if (x == 2148916233L) throw new AuthException("這個 Microsoft 帳號還沒有 Xbox 帳號，請先到 minecraft.net 登入一次。\nThis account has no Xbox profile yet.");
            if (x == 2148916238L) throw new AuthException("未成年帳號需要先加入家庭群組。\nChild accounts must be added to a Family first.");
            if (x == 2148916235L) throw new AuthException("你所在的地區無法使用 Xbox Live。\nXbox Live is not available in your country.");
            throw e;
        }

        JSONObject mc;
        try {
            mc = postJson("https://api.minecraftservices.com/authentication/login_with_xbox",
                    new JSONObject().put("identityToken", "XBL3.0 x=" + uhs + ";" + xsts.getString("Token")), null);
        } catch (HttpError e) {
            if (e.code == 403) throw new BadClientId("這個 Azure 應用程式還沒有獲准使用 Minecraft API（403）。\n到 aka.ms/mce-reviewappid 申請，Mojang 核准後就能登入。");
            throw e;
        }
        String token = mc.getString("access_token");

        JSONObject profile;
        try {
            profile = get("https://api.minecraftservices.com/minecraft/profile", token);
        } catch (HttpError e) {
            if (e.code == 404) throw new AuthException("這個帳號沒有 Minecraft: Java 版（或還沒建立玩家名稱）。\nThis account does not own Minecraft: Java Edition.");
            throw e;
        }
        name = profile.getString("name");
        uuid = profile.getString("id");
        mcToken = token;
        mcTokenExpires = System.currentTimeMillis() + mc.optLong("expires_in", 86400) * 1000L - 60000L;
        SharedPreferences.Editor ed = prefs.edit().putString("name", name).putString("uuid", uuid);
        if (refresh != null) ed.putString("refresh", refresh);
        ed.apply();
        return name;
    }

    /** A valid Minecraft token, refreshing the sign-in if needed (network thread). */
    private static synchronized String token() throws Exception {
        if (mcToken != null && System.currentTimeMillis() < mcTokenExpires) return mcToken;
        String refresh = prefs.getString("refresh", null);
        if (refresh == null || !available()) throw new AuthException("not signed in");
        JSONObject r = postForm(MS + "token", "grant_type=refresh_token&client_id=" + enc(clientId)
                + "&refresh_token=" + enc(refresh) + "&scope=" + enc(SCOPE));
        minecraftFromMicrosoft(r);
        return mcToken;
    }

    /** Tell Mojang's session server we are joining (online-mode servers check this). */
    static boolean joinServer(String serverId) {
        try {
            postJson("https://sessionserver.mojang.com/session/minecraft/join", new JSONObject()
                    .put("accessToken", token()).put("selectedProfile", uuid).put("serverId", serverId), null);
            return true;
        } catch (Exception e) {
            android.util.Log.w("MCPort", "join failed: " + e);
            return false;
        }
    }

    // ---- HTTP ----

    static final class HttpError extends IOException {
        final int code;
        final JSONObject json;
        HttpError(int code, String body) {
            super("HTTP " + code + (body.length() > 0 ? ": " + (body.length() > 200 ? body.substring(0, 200) : body) : ""));
            this.code = code;
            JSONObject j = null;
            try { j = new JSONObject(body); } catch (Exception ignored) {}
            json = j;
        }
    }

    private static JSONObject postForm(String url, String body) throws Exception {
        return request(url, "application/x-www-form-urlencoded", body, null);
    }

    private static JSONObject postJson(String url, JSONObject body, String bearer) throws Exception {
        return request(url, "application/json", body.toString(), bearer);
    }

    private static JSONObject get(String url, String bearer) throws Exception {
        return request(url, null, null, bearer);
    }

    private static JSONObject request(String url, String type, String body, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        if (body != null) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", type);
            OutputStream o = c.getOutputStream();
            o.write(body.getBytes("UTF-8"));
            o.close();
        }
        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        String text = in == null ? "" : new String(readAll(in), "UTF-8");
        if (in != null) in.close();
        c.disconnect();
        if (code >= 400) throw new HttpError(code, text);
        return text.trim().length() == 0 ? new JSONObject() : new JSONObject(text);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
    }

    private static String enc(String s) throws IOException { return URLEncoder.encode(s, "UTF-8"); }
}
