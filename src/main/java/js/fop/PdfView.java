package js.fop;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.util.Date;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import js.http.ContentType;
import js.json.Json;
import js.lang.BugError;
import js.log.Log;
import js.log.LogFactory;
import js.mvc.AbstractView;
import js.template.Template;
import js.template.TemplateEngine;
import js.util.Classes;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;
import org.xml.sax.SAXException;

/**
 * View specialized for PDF documents rendering. This view uses XSL-FO formatted templates to describe PDF document and
 * relies on {@link Template templates engine} for dynamic content injection and on Apache FOP library to actually
 * transform FO into PDF document.
 * 
 * @author Iulian Rotaru
 * @version draft
 */
final class PdfView extends AbstractView
{
  /** Class logger. */
  private static final Log log = LogFactory.getLog(PdfView.class);

  private static final String PROP_PRODUCER = "producer";
  private static final String PROP_CREATOR = "creator";
  private static final String PROP_AUTHOR = "author";
  private static final String PROP_CREATON_DATE = "creation-date";
  private static final String PROP_TITLE = "title";
  private static final String PROP_TARGET_RESOLUTION = "target-resolution";

  /**
   * Apache FO processor factory configuration. This property is merely for testing allowing for alternative
   * configuration. In production points to package resource file.
   */
  private static String FOP_CFG = Classes.getPackageResource(PdfView.class, "fop.xconf");

  /** Content type for PDF documents. */
  private static ContentType CONTENT_TYPE = new ContentType("application/pdf");

  /**
   * FOP factory instance. Excerpt from API: It's important to reuse this instance if you plan to render multiple
   * documents during a JVM's lifetime.
   */
  private FopFactory fopFactory;

  /** Create PDF view instance. */
  public PdfView()
  {
    log.trace("PdfView()");
  }

  @Override
  protected ContentType getContentType()
  {
    return CONTENT_TYPE;
  }

  /**
   * Serialize PDF document on HTTP response output stream.
   * 
   * @throws BugError if view model is null.
   */
  @Override
  protected void serialize(OutputStream outputStream) throws IOException
  {
    if(model == null) {
      throw new BugError("Missing model for PDF view |%s|.", meta.getName());
    }
    if(!(outputStream instanceof BufferedOutputStream)) {
      outputStream = new BufferedOutputStream(outputStream);
    }

    if(fopFactory == null) {
      synchronized(this) {
        if(fopFactory == null) {
          log.debug("Create FOP factory.");

          // set base URL for resource files, e.g. images, to directory where .fo template resides
          URI baseURI = meta.getTemplateFile().getParentFile().toURI();
          log.debug("Set resources base path to |%s|.", baseURI);

          FopConfParser parser;
          try {
            parser = new FopConfParser(Classes.getResourceAsStream(FOP_CFG), baseURI);
          }
          catch(SAXException e) {
            log.error(e);
            throw new IOException(e);
          }
          FopFactoryBuilder builder = parser.getFopFactoryBuilder();
          builder.setStrictUserConfigValidation(true);
          builder.setStrictFOValidation(false);

          fopFactory = builder.build();
        }
      }
    }

    // this method algorithm may seem rather brute force and in a sense it is
    // it loads template file into DOM, uses templates engine to serialize it to a string using a string writer and pass
    // resulting FO string to XML transformer via a string reader
    // anyway, profiling reveals that template processing is only couple percents from total processing time
    // and resulting string is comparable with a small image - a single page template has around 10KB; of course
    // rendering a
    // document with many pages is another story...

    // an alternative to current solution would be to force templates engine to supply a reader and conduct
    // 'serialization'
    // on demand, for chunks or document; for now is only an idea that has no real impact on overall PDF generation
    // performance

    long timestamp = new Date().getTime();

    TemplateEngine templateEngine = Classes.loadService(TemplateEngine.class);
    Template template = templateEngine.getTemplate(meta.getTemplateFile());
    String templateFO = template.serialize(model);

    log.debug("PDF template loading from disk and serialization to string last %d msec. Resulting string size is %d bytes.", new Date().getTime() - timestamp,
        templateFO.length());
    timestamp = new Date().getTime();

    try {
      FOUserAgent agent = fopFactory.newFOUserAgent();
      if(meta.hasProperty(PROP_PRODUCER)) {
        agent.setProducer(meta.getProperty(PROP_PRODUCER));
      }
      if(meta.hasProperty(PROP_CREATOR)) {
        agent.setCreator(meta.getProperty(PROP_CREATOR));
      }
      if(meta.hasProperty(PROP_AUTHOR)) {
        agent.setAuthor(meta.getProperty(PROP_AUTHOR));
      }
      if(meta.hasProperty(PROP_CREATON_DATE)) {
        Json json = Classes.loadService(Json.class);
        agent.setCreationDate((Date)json.parse(meta.getProperty(PROP_CREATON_DATE), Date.class));
      }
      if(meta.hasProperty(PROP_TITLE)) {
        agent.setTitle(meta.getProperty(PROP_TITLE));
      }
      if(meta.hasProperty(PROP_TARGET_RESOLUTION)) {
        agent.setTargetResolution(Integer.parseInt(meta.getProperty(PROP_TARGET_RESOLUTION)));
      }

      Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, agent, outputStream);

      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      Source source = new StreamSource(new StringReader(templateFO));
      Result destination = new SAXResult(fop.getDefaultHandler());
      transformer.transform(source, destination);

    }
    catch(Exception e) {
      log.error("Fail to generate PDF document. Stack trace follows. Dump on template FO on system error output.");
      log.dump(e.getMessage(), e);
      System.err.println(templateFO);
      throw new IOException(e);
    }
    finally {
      // very important to flush output stream otherwise generated PDF document may be incomplete, therefore invalid
      // behavior depend on content size and sometimes could generate valid document
      outputStream.flush();
    }

    log.debug("PDF transformation processing last %d msec.", new Date().getTime() - timestamp);
  }
}
