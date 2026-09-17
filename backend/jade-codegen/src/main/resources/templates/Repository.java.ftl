package ${package}.${module}.repository;

import ${package}.${module}.entity.${className};
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ${className}Repository implements PanacheRepository<${className}> {
}
