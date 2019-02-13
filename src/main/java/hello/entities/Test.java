package hello.entities;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "TEST")
public class Test {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id;

    String message;
}
