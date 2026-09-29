package net.explorviz.code.analysis.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Class to represent a simple Graph for easy cohesion checks. */
public class Graph {

  private final Map<Vertex, List<Vertex>> adjVertices;
  private final Map<String, Vertex> vertexMap;
  private int groupCounter = 1;

  public Graph() {
    adjVertices = new HashMap<>();
    vertexMap = new HashMap<>();
  }

  /**
   * Add a vertex to the graph, defined by its name.
   *
   * @param label the label for the new vertex
   */
  public void addVertex(final String label) {
    final Vertex v = new Vertex(label, groupCounter);
    vertexMap.put(label, v);
    adjVertices.putIfAbsent(v, new ArrayList<>());
    groupCounter++;
  }

  /**
   * Add a vertex to the graph, allows to set the isField flag conveniently.
   *
   * @param label the label for the new vertex
   * @param isField if the current vertex is a field
   */
  public void addVertex(final String label, final boolean isField) {
    final Vertex v = new Vertex(label, groupCounter);
    v.setField(isField);
    vertexMap.put(label, v);
    adjVertices.putIfAbsent(v, new ArrayList<>());
    groupCounter++;
  }

  /**
   * Removes a vertex from the graph.
   *
   * @param label the label of the vertex to be removed
   */
  public void removeVertex(final String label) {
    final Vertex v = vertexMap.get(label);
    adjVertices.values().forEach(e -> e.remove(v));
    adjVertices.remove(v);
    vertexMap.put(label, null);
  }

  /**
   * Adds an edge between two vertices.
   *
   * @param label1 the label for the first vertex
   * @param label2 the label for the second vertex
   */
  public void addEdge(final String label1, final String label2) {
    final Vertex v1 = vertexMap.get(label1);
    final Vertex v2 = vertexMap.get(label2);
    if (v1 == null || v2 == null) {
      return;
    }
    // propagate the group to all connected vertices
    updateGroups(v1, v2);

    adjVertices.get(v1).add(v2);
    adjVertices.get(v2).add(v1);
  }

  /**
   * Gets a set of all group labels contained in the graph.
   *
   * @return the set of group labels
   */
  public Set<Integer> getGroups() {
    final Set<Integer> groupList = new HashSet<>();
    for (final Vertex v : adjVertices.keySet()) {
      if (!v.isField()) {
        groupList.add(v.getGroup());
      }
    }
    return groupList;
  }

  @Override
  public String toString() {
    return adjVertices.toString();
  }

  private void updateGroups(final Vertex v1, final Vertex v2) {
    if (v1.getGroup() != v2.getGroup()) {
      v2.setGroup(v1.getGroup());
      for (final Vertex v : adjVertices.get(v2)) {
        updateGroups(v1, v);
      }
    }
  }
}
