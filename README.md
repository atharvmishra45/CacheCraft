# CampusCache: Intelligent File Cache Simulator

> **Hackathon-Ready LFU + LRU File Cache Simulator**  
> Complete implementation with $O(1)$ operations, Java backend, zero-dependency embedded server, and dark developer dashboard.

---

## 📌 Architecture Overview

CampusCache manages fast memory for high-demand campus documents (e.g. course syllabi, exam schedules, lecture recordings).

It combines **LFU (Least Frequently Used)** eviction with **LRU (Least Recently Used)** tie-breaking:
1. **HashMap<Key, CacheNode>** for $O(1)$ node lookup by key.
2. **HashMap<Frequency, DoublyLinkedList>** for $O(1)$ organization of items by access count.
3. **minFrequency** integer tracker to locate the eviction group in $O(1)$ time without scanning.
4. **DoublyLinkedList** with sentinel `head` (MRU) and `tail` (LRU) to guarantee deterministic tie-breaking in $O(1)$ time.

---

## ⚡ Complexity Guarantees

| Operation | Time Complexity | Auxiliary Space | Explanation |
| :--- | :--- | :--- | :--- |
| **`GET(key)`** | $\mathcal{O}(1)$ average | $\mathcal{O}(1)$ | Hash lookup + pointer unlink/prepend in doubly linked list |
| **`PUT(key, value)`** | $\mathcal{O}(1)$ average | $\mathcal{O}(1)$ | Hash lookup + frequency list eviction/insertion |
| **`STATE`** | $\mathcal{O}(n)$ | $\mathcal{O}(n)$ | Snapshot generation requested strictly on-demand |

---

## 🚀 Quick Start Guide

### Prerequisites
- JDK 17+ (Java 21 supported)

### 1. Compile Core & Tests
```bash
cd CampusCache
javac -d bin src/main/java/campuscache/*.java src/test/java/campuscache/*.java
```

### 2. Run Comprehensive Unit Tests
```bash
java -cp bin campuscache.CampusCacheTest
```
All 12 test suites (48 assertions) covering the 10 specification edge cases will execute and verify algorithm correctness.

### 3. Launch HTTP Server & Dashboard
```bash
java -cp bin campuscache.CampusCacheServer 8080
```
Open **[http://localhost:8080](http://localhost:8080)** in your browser.

---

## 🖥️ REST API Reference

| Endpoint | Method | Payload / Query | Description |
| :--- | :--- | :--- | :--- |
| `/api/get` | `GET` or `POST` | `{"key": "A"}` | Look up document; increments frequency on HIT |
| `/api/put` | `POST` | `{"key": "A", "value": "10"}` | Store or update document |
| `/api/state` | `GET` | _None_ | Retrieve full cache snapshot on-demand |
| `/api/init` | `POST` | `{"capacity": 5}` | Reinitialize fast memory capacity (0–200) |
| `/api/reset` | `POST` | _None_ | Clear cache while retaining current capacity |
| `/api/sequence` | `POST` | `{"sequence": "PUT(A,10)\nGET(A)\nSTATE"}` | Batch process command script |
| `/api/demo` | `GET` | `?type=1` or `?type=2` | Run Hackathon Specification Demo 1 or 2 |

---

## 🏆 Hackathon Presentation & Demos

### Demonstration 1: LFU Eviction under Memory Pressure
- **Capacity**: 2
- **Commands**:
  ```text
  PUT(A, 10)
  PUT(B, 20)
  GET(A)
  PUT(C, 30)
  STATE
  ```
- **Execution flow**:
  1. `PUT(A, 10)` &rarr; Fast memory: `[A: freq 1]`.
  2. `PUT(B, 20)` &rarr; Fast memory: `[B: freq 1, A: freq 1]`.
  3. `GET(A)` &rarr; Returns `10`. Frequency of `A` increments to 2.
  4. `PUT(C, 30)` &rarr; Fast memory full. Evicts `B` (LFU frequency 1 < 2).
  5. `STATE` &rarr; Final cache contains `A` (value 10, freq 2) and `C` (value 30, freq 1).

### Demonstration 2: LRU Tie-Breaking Verification
- **Capacity**: 2
- **Commands**:
  ```text
  PUT(X, 100)
  PUT(Y, 200)
  PUT(Z, 300)
  ```
- **Execution flow**:
  - `X` and `Y` both have frequency 1.
  - `X` was inserted before `Y`, making `X` the Least Recently Used item in frequency group 1.
  - Inserting `Z` evicts `X` deterministically.
