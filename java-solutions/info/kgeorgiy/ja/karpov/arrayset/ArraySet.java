package info.kgeorgiy.ja.karpov.arrayset;

import java.util.*;

public class ArraySet<T> extends AbstractSet<T> implements NavigableSet<T> {
    private final List<T> arr;
    private final Comparator<T> comp;

    public ArraySet() {
        this(List.of());
    }

    public ArraySet(Collection<T> col) {
        this(col, null);
    }

    @SuppressWarnings("unchecked")
    public ArraySet(Collection<T> col, Comparator<T> comp) {
        this.comp = comp == null ? (Comparator<T>) Comparator.naturalOrder() : comp;
        ArrayList<T> tmp = new ArrayList<>(col);
        arr = new ArrayList<>();
        tmp.sort(comp);
        for (int i = 0; i < tmp.size(); i++) {
            if (i == 0 || this.comp.compare(tmp.get(i - 1), tmp.get(i)) != 0) {
                arr.add(tmp.get(i));
            }
        }
    }

    private ArraySet(List<T> col, Comparator<T> comp) {
        this.arr = col;
        this.comp = comp;
    }

    private boolean inBounds(int ind) {
        return ind >= 0 && ind < arr.size();
    }

    private int binarySearch(T val, int presentShift, int absentShift) {
        int colSearch = Collections.binarySearch(arr, val, comp);
        if (colSearch < 0) {
            colSearch = -colSearch - 1 + absentShift;
        } else {
            colSearch += presentShift;
        }
        return inBounds(colSearch) ? colSearch : -1;
    }

    private T binarySearchElement(T val, int presentShift, int absentShift) {
        int res = binarySearch(val, presentShift, absentShift);
        return res != -1 ? arr.get(res) : null;
    }

    @Override
    public T lower(T o) {
        return binarySearchElement(o, -1, -1);
    }

    @Override
    public T floor(T o) {
        return binarySearchElement(o, 0, -1);
    }

    @Override
    public T ceiling(T o) {
        return binarySearchElement(o, 0, 0);
    }

    @Override
    public T higher(T o) {
        return binarySearchElement(o, 1, 0);
    }

    @Override
    public T pollFirst() {
        throw new UnsupportedOperationException("pollFirst unsupported");
    }

    @Override
    public T pollLast() {
        throw new UnsupportedOperationException("pollLast unsupported");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("clear unsupported");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("remove unsupported");
    }

    @Override
    public int size() {
        return arr.size();
    }

    @Override
    public boolean isEmpty() {
        return arr.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return inBounds(Collections.binarySearch(arr, (T) o, comp));
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private final Iterator<? extends T> i = arr.iterator();

            public boolean hasNext() {
                return i.hasNext();
            }

            public T next() {
                return i.next();
            }

            public void remove() {
                throw new UnsupportedOperationException("iterator.remove not supported");
            }
        };
    }

    @Override
    public boolean containsAll(Collection c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public NavigableSet<T> descendingSet() {
        if (arr instanceof ReverseList<T>) {
            return new ArraySet<>(((ReverseList<T>) arr).getBackingList(), Collections.reverseOrder(comp));
        }
        return new ArraySet<>(new ReverseList<>(arr), Collections.reverseOrder(comp));
    }

    @Override
    public Iterator<T> descendingIterator() {
        return descendingSet().iterator();
    }

    private NavigableSet<T> sub(T fromElement, boolean fromInclusive,
                                T toElement, boolean toInclusive, boolean partial) {
        int from = fromInclusive ? binarySearch(fromElement, 0, 0) : binarySearch(fromElement, 1, 0);
        int to = toInclusive ? binarySearch(toElement, 0, -1) : binarySearch(toElement, -1, -1);
        int compRes = comp.compare(fromElement, toElement);
        if (compRes > 0 && !partial) {
            throw new IllegalArgumentException("From element is bigger than to element");
        }
        if (from > to || from == -1 || to == -1 || compRes > 0) {
            return new ArraySet<>();
        }
        return new ArraySet<>(arr.subList(from, to + 1), comp);
    }

    @Override
    public NavigableSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
        return sub(fromElement, fromInclusive, toElement, toInclusive, false);
    }

    @Override
    public NavigableSet<T> headSet(T toElement, boolean inclusive) {
        return arr.isEmpty() ? new ArraySet<>() : sub(first(), true, toElement, inclusive, true);
    }

    @Override
    public NavigableSet<T> tailSet(T fromElement, boolean inclusive) {
        return arr.isEmpty() ? new ArraySet<>() : sub(fromElement, inclusive, last(), true, true);
    }

    @Override
    public Comparator<T> comparator() {
        return comp.equals(Comparator.naturalOrder()) ? null : comp;
    }

    @Override
    public NavigableSet<T> subSet(T fromElement, T toElement) {
        return subSet(fromElement, true, toElement, false);
    }

    @Override
    public NavigableSet<T> headSet(T toElement) {
        return arr.isEmpty() ? new ArraySet<>() : headSet(toElement, false);
    }

    @Override
    public NavigableSet<T> tailSet(T fromElement) {
        return arr.isEmpty() ? new ArraySet<>() : tailSet(fromElement, true);
    }

    @Override
    public T first() {
        if (arr.isEmpty()) {
            throw new NoSuchElementException();
        }
        return arr.get(0);
    }

    @Override
    public T last() {
        if (arr.isEmpty()) {
            throw new NoSuchElementException();
        }
        return arr.get(arr.size() - 1);
    }
}
