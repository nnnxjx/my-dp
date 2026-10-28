package com.hmdp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.AuthorizationScope;
import springfox.documentation.service.Contact;
import springfox.documentation.service.SecurityReference;
import springfox.documentation.service.SecurityScheme;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.Collections;
import java.util.List;

/**
 * Swagger（springfox 3.0.0）接口文档配置
 * 文档页面：http://localhost:8081/swagger-ui/index.html
 * JSON 文档：http://localhost:8081/v3/api-docs
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public Docket defaultApi() {
        return new Docket(DocumentationType.OAS_30)
                .enable(true)
                .apiInfo(apiInfo())
                .select()
                // 扫描本项目 controller 包下的所有接口
                .apis(RequestHandlerSelectors.basePackage("com.hmdp.controller"))
                .paths(PathSelectors.any())
                .build()
                // 全局 authorization 请求头，登录后无需每个接口单独填 token
                .securitySchemes(securitySchemes())
                .securityContexts(securityContexts());
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("黑马点评接口文档")
                .description("hmdp 项目 RESTful 接口文档，由 springfox 3.0.0 自动生成")
                .contact(new Contact("devin03", "", ""))
                .version("1.0")
                .build();
    }

    private List<SecurityScheme> securitySchemes() {
        // 声明一个名为 authorization 的 header 参数
        return Collections.<SecurityScheme>singletonList(new ApiKey("Authorization", "authorization", "header"));
    }

    private List<SecurityContext> securityContexts() {
        AuthorizationScope[] scopes = {new AuthorizationScope("global", "全部接口")};
        SecurityReference reference = new SecurityReference("Authorization", scopes);
        return Collections.singletonList(SecurityContext.builder()
                .securityReferences(Collections.singletonList(reference))
                // 除登录注册外，其余接口默认携带 token
                .operationSelector(ctx -> !ctx.requestMappingPattern().startsWith("/user/"))
                .build());
    }
}
