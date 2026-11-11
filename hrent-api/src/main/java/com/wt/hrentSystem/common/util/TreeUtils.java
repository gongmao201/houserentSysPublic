package com.wt.hrentSystem.common.util;

import com.wt.hrentSystem.common.entity.Menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单树构建工具
 * 说明：原始发布包缺失该类，MenuService.findList 依赖，此为重建实现。
 * 依据 parent_id 将扁平菜单列表组装为树形结构，并按 sort 排序。
 */
public class TreeUtils {

    private TreeUtils() {
    }

    public static List<Menu> getTreeData(List<Menu> list) {
        List<Menu> roots = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return roots;
        }
        Map<String, Menu> map = new HashMap<>(list.size() * 2);
        for (Menu menu : list) {
            map.put(menu.getId(), menu);
        }
        for (Menu menu : list) {
            String parentId = menu.getParentId();
            Menu parent = parentId == null ? null : map.get(parentId);
            if (parent != null && !parent.getId().equals(menu.getId())) {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(menu);
                parent.setExistChildren(true);
            } else {
                roots.add(menu);
            }
        }
        sortTree(roots);
        return roots;
    }

    private static void sortTree(List<Menu> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        list.sort(Comparator.comparingInt(m -> m.getSort() == null ? 30 : m.getSort()));
        for (Menu menu : list) {
            sortTree(menu.getChildren());
        }
    }
}
