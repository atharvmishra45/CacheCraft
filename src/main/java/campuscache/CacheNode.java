package campuscache;

/**
 * CacheNode represents an entry in the CampusCache.
 * Stores key, value, access frequency, and doubly linked list pointers
 * for O(1) removal and insertion.
 */
public class CacheNode {
    public String key;
    public String value;
    public int frequency;
    public CacheNode prev;
    public CacheNode next;

    public CacheNode(String key, String value) {
        this.key = key;
        this.value = value;
        this.frequency = 1;
        this.prev = null;
        this.next = null;
    }

    @Override
    public String toString() {
        return "CacheNode{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", frequency=" + frequency +
                '}';
    }
}
