package controllers;

import org.wisdom.api.DefaultController;
import org.wisdom.api.http.MimeTypes;
import org.wisdom.api.http.Result;
import org.wisdom.api.templates.Template;

import java.util.Collections;
import java.util.List;

public class ResultExample extends DefaultController {

    //@View("template")
    Template template;

    public void example() {
        List<String> errors = Collections.emptyList();
        // tag::results[]
        Result ok = ok("Hello wisdom!");
        Result page = ok(render(template));
        Result notFound = notFound();
        Result pageNotFound = notFound("<h1>Page not found</h1>").as(MimeTypes.HTML);
        Result badRequest = badRequest(render(template, "error", errors));
        Result oops = internalServerError("Oops");
        Result exception = internalServerError(new NullPointerException("Cannot be null"));
        Result anyStatus = status(488).render("Strange response").as(MimeTypes.TEXT);
        // end::results[]
    }

}
