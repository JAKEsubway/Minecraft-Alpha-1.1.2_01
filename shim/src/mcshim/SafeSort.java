package mcshim;

import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

/**
 * Drop-in for Collections.sort / Arrays.sort used by the game. Some of Alpha's
 * comparators (chunk render order) are inconsistent, and TimSort on modern
 * Java and on Android throws "Comparison method violates its general contract!"
 * when it notices. This plain stable merge sort never throws for that reason.
 */
public final class SafeSort {
    private SafeSort() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void sort(List list, Comparator c) {
        Object[] a = list.toArray();
        sort(a, c);
        ListIterator it = list.listIterator();
        for (Object o : a) { it.next(); it.set(o); }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void sort(Object[] a, Comparator c) {
        if (a.length < 2) return;
        Object[] tmp = new Object[a.length];
        mergeSort(a, tmp, 0, a.length, c);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void mergeSort(Object[] a, Object[] tmp, int lo, int hi, Comparator c) {
        if (hi - lo < 8) { // insertion sort for small runs
            for (int i = lo + 1; i < hi; i++) {
                Object x = a[i];
                int j = i - 1;
                while (j >= lo && c.compare(a[j], x) > 0) { a[j + 1] = a[j]; j--; }
                a[j + 1] = x;
            }
            return;
        }
        int mid = (lo + hi) >>> 1;
        mergeSort(a, tmp, lo, mid, c);
        mergeSort(a, tmp, mid, hi, c);
        if (c.compare(a[mid - 1], a[mid]) <= 0) return;
        System.arraycopy(a, lo, tmp, lo, hi - lo);
        int i = lo, j = mid, k = lo;
        while (i < mid && j < hi) a[k++] = c.compare(tmp[j], tmp[i]) < 0 ? tmp[j++] : tmp[i++];
        while (i < mid) a[k++] = tmp[i++];
        while (j < hi) a[k++] = tmp[j++];
    }
}
