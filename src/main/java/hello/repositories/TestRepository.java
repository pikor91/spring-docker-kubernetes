package hello.repositories;

import hello.entities.Test;
import org.springframework.data.repository.CrudRepository;

public interface TestRepository extends CrudRepository <Test, Long> {
}
