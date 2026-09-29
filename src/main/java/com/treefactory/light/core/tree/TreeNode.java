package com.treefactory.light.core.tree;

/** Parent always precedes the child, allowing iterative reverse-order pipe evaluation. */
public record TreeNode(Vector3 position, int parent, double radius) {
    public TreeNode withRadius(double radius) { return new TreeNode(position, parent, radius); }
}
