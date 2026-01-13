package ru.fuctorial.fuctorize.utils;

import java.lang.reflect.Field;
import java.util.*;






public class SmartMovingConfigScanner {

    public static Map<String, List<Object>> scan(Object smOptionsInstance) {
        Map<String, List<Object>> categorized = new LinkedHashMap<>();
        if (smOptionsInstance == null) return categorized;

        try {
            // --- ГЛАВНОЕ ИЗМЕНЕНИЕ: Собираем поля со всей иерархии классов ---
            List<Field> allFields = new ArrayList<>();
            Class<?> currentClass = smOptionsInstance.getClass();
            while (currentClass != null && currentClass != Object.class) {
                allFields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
                currentClass = currentClass.getSuperclass();
            }
            // --- КОНЕЦ ИЗМЕНЕНИЯ ---

            String currentBook = "Uncategorized"; // Категория по умолчанию

            // Smart Moving properties_list отсортирован по-умолчанию, поэтому мы можем "идти" по ним и менять категорию
            // когда встречаем .book("..."), но для надежности лучше так:
            for (Field field : allFields) {
                if (field.getType().getName().equals("net.smart.properties.Property")) {
                    field.setAccessible(true);
                    Object prop = field.get(smOptionsInstance);
                    if (prop == null) continue;

                    Field headerField = prop.getClass().getDeclaredField("header");
                    headerField.setAccessible(true);
                    String[] header = (String[]) headerField.get(prop);

                    Field gapField = prop.getClass().getDeclaredField("gap");
                    gapField.setAccessible(true);
                    int gap = (int) gapField.get(prop);

                    // gap = 3 соответствует вызову .book(), что означает новую категорию
                    if (header != null && header.length > 0 && gap == 3) {
                        currentBook = header[0];
                    }

                    categorized.computeIfAbsent(currentBook, k -> new ArrayList<>()).add(prop);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return categorized;
    }
}