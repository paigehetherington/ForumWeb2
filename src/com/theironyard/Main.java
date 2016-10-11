package com.theironyard;

import spark.ModelAndView;
import spark.Session;
import spark.Spark;
import spark.template.mustache.MustacheTemplateEngine;

import java.util.ArrayList;
import java.util.HashMap;

public class Main {
    static HashMap<String, User> users = new HashMap<>();
    static ArrayList<Message> messages = new ArrayList<>();

    public static void main(String[] args) {
	// write your code here
        users.put("Alice", new User("Alice", "pass"));
        users.put("Bob", new User("Bob", "pass"));
        users.put("Charlie", new User("Charlie", "pass"));

        messages.add(new Message(0, -1, "Alice", "Hello everyone!"));
        messages.add(new Message(1, -1, "Bob", "This is another thread!"));
        messages.add(new Message(2, 0, "Charlie", "Cool thread, Alice!"));
        messages.add(new Message(3, 2, "Alice", "Thanks Charlie!"));

        Spark.get(
                "/",
                (request, response) -> {
                    //get query parameter
                    String replyId = request.queryParams("replyId");
                    int replyIdNum = -1;
                    if (replyId != null) {
                        replyIdNum = Integer.valueOf(replyId);
                    }

                    Session session = request.session();
                    String name = session.attribute("loginName"); //pulling name from session (1 argument)


                    HashMap m = new HashMap();
                    ArrayList<Message> msgs = new ArrayList<Message>(); //don't display all messages
                    for (Message message : messages) {
                       if (message.replyId == replyIdNum) {
                           msgs.add(message);
                       }
                    }
                    m.put("messages",msgs);
                    m.put("name", name);
                    m.put("replyId", replyIdNum);
                    return new ModelAndView(m, "home.html");
                },
                new MustacheTemplateEngine()
        );

        Spark.post(
                "/login",
                ((request, response) -> {
                    String name = request.queryParams("loginName");
                    String pass = request.queryParams("password");
                    User user = users.get(name);
                    if (user == null) {
                        user = new User(name, pass);
                        users.put(name, user);
                    }
                    else if (!pass.equals(user.password)) {
                        Spark.halt(403); //if password doesn't match, throw error - 403 is forbidden error
                        return null;
                    }
                    Session session = request.session();
                    session.attribute("loginName", name); //sets session (2 arguments)
                    response.redirect("/");
                    return null;
                }
        ));

        Spark.post(
                "/create-message",
                ((request, response) -> {
                    String text = request.queryParams("text");
                    int replyId = Integer.valueOf(request.queryParams("replyId"));
                    Session session = request.session();
                    String name = session.attribute("loginName");
                    Message msg = new Message(messages.size(), replyId, name, text ); //hidden field in header.html creates loop
                    messages.add(msg);
                    response.redirect("/");
                    return null;
                })
        );


    }
}
