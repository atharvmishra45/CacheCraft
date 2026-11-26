package campuscache;

import java.util.ArrayList;
import java.util.List;

/**
 * DoublyLinkedList represents items sharing the exact same access frequency.
 * Head represents the Most Recently Used (MRU) item.
 * Tail represents the Least Recently Used (LRU) item.
 * 
 * Supports O(1) addFirst, O(1) remove, and O(1) removeLast (LRU eviction).
 */
public class DoublyLinkedList {
    private final CacheNode head;
    private final CacheNode tail;
    private int size;

    public DoublyLinkedList() {
        head = new CacheNode(null, null); // dummy head (MRU sentinel)
        tail = new CacheNode(null, null); // dummy tail (LRU sentinel)
        head.next = tail;
        tail.prev = head;
        size = 0;
    }

    /**
     * Inserts node at the head (Most Recently Used position).
     */
    public void addFirst(CacheNode node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
        size++;
    }

    /**
     * Removes an arbitrary node from the list in O(1).
     */
    public void remove(CacheNode node) {
        if (node.prev != null) {
            node.prev.next = node.next;
        }
        if (node.next != null) {
            node.next.prev = node.prev;
        }
        node.prev = null;
        node.next = null;
        size--;
    }

    /**
     * Evicts and returns the least recently used node (before tail sentinel) in O(1).
     * Returns null if empty.
     */
    public CacheNode removeLast() {
        if (isEmpty()) {
            return null;
        }
        CacheNode lruNode = tail.prev;
        remove(lruNode);
        return lruNode;
    }

    /**
     * Returns the least recently used node without removing it.
     */
    public CacheNode peekLast() {
        if (isEmpty()) {
            return null;
        }
        return tail.prev;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    /**
     * Returns nodes in order from MRU to LRU for state inspection.
     */
    public List<CacheNode> toList() {
        List<CacheNode> list = new ArrayList<>();
        CacheNode curr = head.next;
        while (curr != tail && curr != null) {
            list.add(curr);
            curr = curr.next;
        }
        return list;
    }
}
