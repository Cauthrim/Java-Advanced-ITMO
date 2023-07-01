package info.kgeorgiy.ja.karpov.arrayset;

import java.util.AbstractList;
import java.util.List;

public class ReverseList<E> extends AbstractList<E>{
    private final List<E> backingList;

    public ReverseList(List<E> backingList){
        this.backingList = backingList;
    }

    public List<E> getBackingList() {
        return backingList;
    }

    @Override
    public E get(int i) {
        return backingList.get(backingList.size() - i - 1);
    }

    @Override
    public int size() {
        return backingList.size();
    }
}