package io.ejangs.docsa.domain.save.api;

import io.swagger.v3.oas.annotations.Operation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
    summary = "유저가 요청한 saveId에 해당하는 저장 조회",
)
public @interface GetSaveDocs {
}
