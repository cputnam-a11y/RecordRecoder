package recordrecoder.impl.utils;

public class ClassNameHelper {
    public static String toDescName(String className) {
        if (!className.startsWith("L"))
            className = "L" + className;
        if (!className.endsWith(";"))
            className += ";";
        if (className.contains("."))
            className = className.replace('.', '/');
        return className;
    }

    public static String toBinaryName(String className) {
        if (className.startsWith("L"))
            className = className.substring(1);
        if (className.endsWith(";"))
            className = className.substring(0, className.length() - 1);
        if (className.contains("/"))
            className = className.replace('/', '.');
        return className;
    }

    public static String toInternalName(String className) {
        if (className.startsWith("L"))
            className = className.substring(1);
        if (className.endsWith(";"))
            className = className.substring(0, className.length() - 1);
        if (className.contains("."))
            className = className.replace('.', '/');
        return className;
    }
}
