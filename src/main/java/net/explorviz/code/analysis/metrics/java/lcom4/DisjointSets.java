package net.explorviz.code.analysis.metrics.java.lcom4;

import java.util.HashMap;
import java.util.Map;

/** Union-find structure to determine connected components. */
/* package */ final class DisjointSets {

  private final Map<Object, Object> parents = new HashMap<>();

  /** Returns the representative of the set that contains the element. */
  /* package */ Object find(final Object element) {
    Object root = element;
    while (parents.containsKey(root)) {
      root = parents.get(root);
    }
    Object current = element;
    while (!current.equals(root)) {
      current = parents.put(current, root);
    }
    return root;
  }

  /** Merges the sets that contain the given elements. */
  /* package */ void union(final Object first, final Object second) {
    final Object firstRoot = find(first);
    final Object secondRoot = find(second);
    if (!firstRoot.equals(secondRoot)) {
      parents.put(firstRoot, secondRoot);
    }
  }
}
