package hello;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev2")
public class HelloDev2 {
    @RequestMapping
    public String greeting (){
        return "Hello ponaszki on docker with profile dev2.";
    }
}
