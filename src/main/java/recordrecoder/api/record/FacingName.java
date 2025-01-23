package recordrecoder.api.record;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ /* No targets allowed */})
@Retention(RetentionPolicy.RUNTIME)
public @interface FacingName {
    String value();
}
