package org.example;

import io.javalin.Javalin;
import org.example.model.UrlMapping;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.io.Serializable;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;


public class HttpServer {

    private static final SessionFactory sessionFactory = HibernateHelper.getSessionFactory();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(9000);
        app.get("/", context -> context
                .contentType("text/html")
                .result("<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "<head>\n" +
                        "\t<title>URL Shortener</title>\n" +
                        "<style>"+
                        ".root{" +
                        "background-color:  #FFFFFF;" +
                        "}" +
                        ".ShortenButton{" +
                        "margin: 28px 0;" +
                        "background: #0B1F67;" +
                        "color: white;" +
                        "}" +
                        ".urlInput{"+
                        "width: 490px;" +
                        " height: 30px;" +
                        " border: 1px solid #999999;" +
                        " padding: 5px; " +
                        "margin: 32px 0;" +
                        "margin-right: 10px" +
                        "}" +
                        ".h1tag{" +
                        "display: flex;"+
                        "justify-content: center;" +
                        "}" +
                        ".form{" +
                        "display: flex;"+
                        "justify-content: center;" +
                        "background: #0B1736;" +
                        "}"+
                        "</style>" +
                        "</head>\n" +
                        "<body class=\"root\">\n" +
                        "<h1 class=\"h1tag\">Short links, big results</h1>" +
                        "<form class=\"form\" method=\"post\">\n" +
                        "\t<input class=\"urlInput\" name=\"url\" type=\"text\" />\n" +
                        "<button class=\"ShortenButton\"type=\"submit\">Shorten</button>" +
                        "</form>\n" +
                        "</body>\n" +
                        "</html>")
        );

        app.post("/", context -> {
            try (Session session = sessionFactory.openSession()) {
                String url = context.formParam("url");
                String shortUrl = ensureUniqueness(session, () -> generateRandomString(10));

                UrlMapping urlMapping = new UrlMapping();
                urlMapping.setUrl(url);
                urlMapping.setShortUrl(shortUrl);


                Transaction transaction = session.beginTransaction();
                Serializable urlMappingId = session.save(urlMapping);
                System.out.println(urlMappingId);

                String html = ("<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "<head>\n" +
                        "\t<title>URL Shortener</title>\n" +
                        "<style>"+
                        ".root{" +
                        "background-color: #FFFFFF;" +
                        "}" +
                        ".ShortenButton{" +
                        "margin: 28px 0;" +
                        "background: #0B1F67;" +
                        "color: white;" +
                        "}" +
                        ".urlInput{"+
                        "width: 490px;" +
                        " height: 30px;" +
                        " border: 1px solid #999999;" +
                        " padding: 5px; " +
                        "margin: 32px 0;" +
                        "margin-right: 10px" +
                        "}" +
                        ".h1tag{" +
                        "display: flex;"+
                        "justify-content: center;" +
                        "}" +
                        ".refContainer{" +
                        "display: flex;" +
                        "justify-content: center;" +
                        "}" +
                        ".form{" +
                        "background: #0B1736;" +
                        "display: flex;" +
                        "justify-content: center;" +
                        "}"+
                        "</style>" +
                        "</head>\n" +
                        "<body class=\"root\">\n" +
                        "<h1 class=\"h1tag\">Short links, big results</h1>" +
                        "<form class=\"form\" method=\"post\">\n" +
                        "\t<input class=\"urlInput\" name=\"url\" type=\"text\" />\n" +
                        "<button class=\"ShortenButton\"type=\"submit\">Shorten</button>" +
                        "</form>\n" +
                        "<a class=\"refContainer\"href=\"PLACEHOLDER\">PLACEHOLDER</a>\n" +
                        "</body>\n" +
                        "</html>").replaceAll("PLACEHOLDER", "http://localhost:9000/" + shortUrl);

                context
                        .contentType("text/html")
                        .result(html);
                transaction.commit();
            }
        });

        app.get("/:shortUrl", context -> {
            String shortUrl = context.pathParam("shortUrl");

            String targetUrl = "";
            try (Session session = sessionFactory.openSession()) {

                Transaction transaction = session.beginTransaction();
                try {
                    final Query<UrlMapping> query = session
                            .createQuery("select u from UrlMapping u where u.shortUrl = :shortUrl", UrlMapping.class)
                            .setParameter("shortUrl", shortUrl);
                    List<UrlMapping> urlMappings = query.getResultList();
                    if (urlMappings != null && !urlMappings.isEmpty()) {
                        targetUrl = urlMappings.get(0).getUrl();
                        transaction.commit();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    transaction.rollback();
                }
            }
            context.header("Location", targetUrl)
                    .status(302);
        });
    }


    private static String ensureUniqueness(Session session, Supplier<String> supplier) {
        boolean isUnique = false;
        String value = null;
        Transaction transaction = session.beginTransaction();

        while (!isUnique) {
            value = supplier.get();
            try {
                final Query<UrlMapping> query = session
                        .createQuery("select u from UrlMapping u where shortUrl = :shortUrl", UrlMapping.class)
                        .setParameter("shortUrl", value);
                List<UrlMapping> urlMappings = query.getResultList();
                if (urlMappings != null && urlMappings.isEmpty()) {
                    isUnique = true;
                    transaction.commit();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return value;
    }

    private static String generateRandomString(@SuppressWarnings("SameParameterValue") int length) {
        char[] chars = new char[length];

        Random random = new Random();
        for (int i = 0; i < length; i++) {
            char currentChar = (char) (random.nextInt(26) + 1 | 64);
            if (Math.random() > 0.5) {
                currentChar |= 32;
            }
            chars[i] = currentChar;
        }
        return new String(chars);
    }
}
