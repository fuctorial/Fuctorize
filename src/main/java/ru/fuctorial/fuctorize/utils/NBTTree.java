package ru.fuctorial.fuctorize.utils;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

public class NBTTree {

    private NBTTagCompound baseTag;
    private Node<NamedNBT> root;
    public static final NBTNodeSorter SORTER = new NBTNodeSorter();

    public NBTTree(NBTTagCompound tag) {
        this.baseTag = tag;
        this.construct();
        if (this.root != null) {
            this.root.setDrawChildren(true);
        }
    }

     
    public void fromNBTTagCompound(NBTTagCompound newTag) {
        this.baseTag = newTag;
        this.construct();
        if (this.root != null) {
            this.root.setDrawChildren(true);
        }
    }

    public Node<NamedNBT> getRoot() {
        return this.root;
    }

    public boolean canDelete(Node<NamedNBT> node) {
        return node != this.root;
    }

    public boolean delete(Node<NamedNBT> node) {
        if (node == null || node == this.root) {
            return false;
        }
        return this.deleteNode(node, this.root);
    }

    private boolean deleteNode(Node<NamedNBT> toDelete, Node<NamedNBT> cur) {
        Iterator<Node<NamedNBT>> it = cur.getChildren().iterator();
        while (it.hasNext()) {
            Node<NamedNBT> child = it.next();
            if (child == toDelete) {
                it.remove();
                return true;
            }
            if (this.deleteNode(toDelete, child)) {
                return true;
            }
        }
        return false;
    }

    private void construct() {
        this.root = new Node<>(new NamedNBT("ROOT", ((NBTTagCompound) this.baseTag.copy())));
        this.addChildrenToTree(this.root);
        this.sort(this.root);
    }

    public void sort(Node<NamedNBT> node) {
        Collections.sort(node.getChildren(), SORTER);
        for (Node<NamedNBT> c : node.getChildren()) {
            this.sort(c);
        }
    }

    public void addChildrenToTree(Node<NamedNBT> parent) {
        NBTBase tag = parent.getObject().getNBT();
        if (tag instanceof NBTTagCompound) {
            Map<String, NBTBase> map = NBTHelper.getMap((NBTTagCompound) tag);
            for (Map.Entry<String, NBTBase> entry : map.entrySet()) {
                Node<NamedNBT> child = new Node<>(parent, new NamedNBT(entry.getKey(), entry.getValue()));
                parent.addChild(child);
                this.addChildrenToTree(child);
            }
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            for (int i = 0; i < list.tagCount(); ++i) {
                NBTBase base = NBTHelper.getTagAt(list, i);
                if (base != null) {
                    Node<NamedNBT> child = new Node<>(parent, new NamedNBT(base));
                    parent.addChild(child);
                    this.addChildrenToTree(child);
                }
            }
        }
    }

    public NBTTagCompound toNBTTagCompound() {
        NBTTagCompound tag = new NBTTagCompound();
        this.addChildrenToTag(this.root, tag);
        return tag;
    }

    public void addChildrenToTag(Node<NamedNBT> parent, NBTTagCompound tag) {
        for (Node<NamedNBT> child : parent.getChildren()) {
            NBTBase base = child.getObject().getNBT();
            String name = child.getObject().getName();
            if (base instanceof NBTTagCompound) {
                NBTTagCompound newTag = new NBTTagCompound();
                this.addChildrenToTag(child, newTag);
                tag.setTag(name, newTag);
            } else if (base instanceof NBTTagList) {
                NBTTagList list = new NBTTagList();
                this.addChildrenToList(child, list);
                tag.setTag(name, list);
            } else {
                tag.setTag(name, base.copy());
            }
        }
    }

    public void addChildrenToList(Node<NamedNBT> parent, NBTTagList list) {
        if (!parent.getChildren().isEmpty()) {
            byte listType = parent.getChildren().get(0).getObject().getNBT().getId();
            NBTHelper.setListTagType(list, listType);
        }

        for (Node<NamedNBT> child : parent.getChildren()) {
            NBTBase base = child.getObject().getNBT();
            if (base instanceof NBTTagCompound) {
                NBTTagCompound newTag = new NBTTagCompound();
                this.addChildrenToTag(child, newTag);
                list.appendTag(newTag);
            } else if (base instanceof NBTTagList) {
                NBTTagList newList = new NBTTagList();
                this.addChildrenToList(child, newList);
                list.appendTag(newList);
            } else {
                list.appendTag(base.copy());
            }
        }
    }
}