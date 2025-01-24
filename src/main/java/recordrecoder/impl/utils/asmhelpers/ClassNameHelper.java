package recordrecoder.impl.utils.asmhelpers;

public class ClassNameHelper {
    public static String toDescriptor(String className) {
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
