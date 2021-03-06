package com.theironyard;

import com.sun.corba.se.spi.activation.Server;
import spark.ModelAndView;
import spark.Session;
import spark.Spark;
import spark.template.mustache.MustacheTemplateEngine;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReadWriteLock;

public class Main {
//    static HashMap<String, User> users = new HashMap<>();
//    static ArrayList<Message> messages = new ArrayList<>();

    //converting to use db
    public static void createTables(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS users(id IDENTITY, name VARCHAR, password VARCHAR)");
        stmt.execute("CREATE TABLE IF NOT EXISTS messages(id IDENTITY, reply_id INT, text VARCHAR, user_id INT)");
    }

    public static void insertUser(Connection conn, String name, String password) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO users VALUES (NULL, ?, ?)");
        stmt.setString(1, name);
        stmt.setString(2, password);
        stmt.execute();
    }

    public static User selectUser(Connection conn, String name) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE name = ?");
        stmt.setString(1, name);
        ResultSet results = stmt.executeQuery();
        if (results.next()) {
            int id = results.getInt("id");
            String password = results.getString("password");
            return new User(id, name, password);
        }
        return null;
    }

    public static void insertMessage(Connection conn, int replyId, String text, int userId) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO messages VALUES (NULL, ?, ?, ?)");
        stmt.setInt(1, replyId);
        stmt.setString(2, text);
        stmt.setInt(3, userId);
        stmt.execute();
    }

    public static Message selectMessage(Connection conn, int id) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM messages INNER JOIN users ON +" +
                "messages.user_id = users.id WHERE messages.id = ?");
        //looking for specific message so messages.id
        stmt.setInt(1, id);
        ResultSet results = stmt.executeQuery();
        if (results.next()) {
            int replyId = results.getInt("messages.reply_id"); //when doing inner joins prefix ids for different table names
            String text = results.getString("messages.text");
            String author = results.getString("users.name");
            return new Message(id, replyId, author, text);
        }
        return null;

    }

    public static ArrayList<Message> selectReplies(Connection conn, int replyId) throws SQLException {
        ArrayList<Message> replies = new ArrayList<>();
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM messages INNER JOIN users ON  +" +
                "messages.user_id = users.id WHERE messages.reply_id = ?");
        stmt.setInt(1, replyId);
        ResultSet results = stmt.executeQuery();
        while (results.next()) {
            int id = results.getInt("messages.id");
            String text = results.getString("messages.text");
            String author = results.getString("users.name");
            replies.add(new Message(id, replyId, author, text));
        }
        return  replies;
    }


    public static void main(String[] args) throws SQLException {
	// write your code here

        Server.createWebServer().start();
        Connection conn = DriverManager.getConnection("jdbc:h2:./main");
        createTables(conn);

//        users.put("Alice", new User("Alice", "pass"));
//        users.put("Bob", new User("Bob", "pass"));
//        users.put("Charlie", new User("Charlie", "pass"));
//
//        messages.add(new Message(0, -1, "Alice", "Hello everyone!"));
//        messages.add(new Message(1, -1, "Bob", "This is another thread!"));
//        messages.add(new Message(2, 0, "Charlie", "Cool thread, Alice!"));
//        messages.add(new Message(3, 2, "Alice", "Thanks Charlie!"));

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
                    String name = session.attribute("l" +
                            "oginName"); //pulling name from session (1 argument)


                    HashMap m = new HashMap();
                    ArrayList<Message> msgs = selectReplies(conn, replyIdNum);   //new ArrayList<Message>(); //don't display all messages
//                    for (Message message : messages) {
//                       if (message.replyId == replyIdNum) {
//                           msgs.add(message);
//                       }
//                    }
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
                    User user = selectUser(conn, name);
                    if (user == null) {
                        insertUser(conn, name, pass);

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
                    User user = selectUser(conn, name);
                    if (user == null) {
                        throw new Exception("Not logged in!");
                    }
                    insertMessage(conn, replyId, text, user.id));
                    response.redirect(request.headers("Referer")); //htttp referers (misspelled), keeps on same page
                    return null;
                })
        );

        Spark.post(
                "/logout",
                (request, response) -> {
                    Session session = request.session();
                    session.invalidate();
                    response.redirect(request.headers("Referer"));
                    return null;

        }
        );

    }


    }
}
