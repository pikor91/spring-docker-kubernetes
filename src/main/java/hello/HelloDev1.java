package hello;

import hello.entities.Test;
import hello.repositories.TestRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RestController
@Profile("!dev2")
public class HelloDev1 {
    Logger fileLog = LoggerFactory.getLogger("PS_LOG");
    Logger consoleLog = LoggerFactory.getLogger("CONSOLE");


    @Autowired
    TestRepository testRepository;

    @RequestMapping(path = "/fileLog", method = RequestMethod.POST)
    public String logFile(@RequestBody String body){
        fileLog.info(body);
        return "Message written to file log:"+body;
    }

    @RequestMapping(path = "/consoleLog", method = RequestMethod.POST)
    public String test(@RequestBody String body){
        consoleLog.info(body);
        return "Message written to console:"+body;
    }

    @RequestMapping(path = "/save", method = RequestMethod.POST)
    public String getFromDatabase(@RequestBody String body){
        fileLog.info(body);
        consoleLog.info(body);
        Test t = new Test();
        t.setMessage(body);
        Test save = testRepository.save(t);
        return "Saved entity: "+ save.toString();
    }
}
