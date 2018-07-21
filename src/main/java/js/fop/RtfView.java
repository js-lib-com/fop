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
 * View for RTF documents based on XSL-FO template. RTF stands for Rich Text Format and is developed by Microsoft. Most word
 * processors are able to read and write some versions of RTF.
 * <p>
 * This view uses XSL-FO formatted templates to describe RTF document and relies on {@link Template templates engine} for
 * dynamic content injection. It uses Apache FOP library to actually transform FO into RTF document.
 * 
 * @author Iulian Rotaru
 * @version draft
 */
final class RtfView extends AbstractView {
	/** Class logger. */
	private static final Log log = LogFactory.getLog(RtfView.class);

	private static final String PROP_PRODUCER = "producer";
	private static final String PROP_CREATOR = "creator";
	private static final String PROP_AUTHOR = "author";
	private static final String PROP_CREATON_DATE = "creation-date";
	private static final String PROP_TITLE = "title";
	private static final String PROP_TARGET_RESOLUTION = "target-resolution";

	/**
	 * Apache FO processor factory configuration. This property is merely for testing allowing for alternative configuration. In
	 * production points to package resource file.
	 */
	private static String FOP_CFG = Classes.getPackageResource(RtfView.class, "fop.xconf");

	/** Content type for RTF documents. */
	private static ContentType CONTENT_TYPE = new ContentType("application/rtf");

	/**
	 * FOP factory instance. Excerpt from API: It's important to reuse this instance if you plan to render multiple documents
	 * during a JVM's lifetime.
	 */
	private FopFactory fopFactory;

	public RtfView() {
		log.trace("RtfView()");
	}

	@Override
	protected ContentType getContentType() {
		return CONTENT_TYPE;
	}

	/**
	 * Serialize RTF document on HTTP response output stream.
	 * 
	 * @throws BugError if view model is null.
	 */
	@Override
	protected void serialize(OutputStream outputStream) throws IOException {
		if (model == null) {
			throw new BugError("Missing model for RTF view |%s|.", meta.getName());
		}
		if (!(outputStream instanceof BufferedOutputStream)) {
			outputStream = new BufferedOutputStream(outputStream);
		}

		if (fopFactory == null) {
			synchronized (this) {
				if (fopFactory == null) {
					log.debug("Create FOP factory");

					// set base URL for resource files, e.g. images, to directory where .fo template resides
					URI baseURI = meta.getTemplateFile().getParentFile().toURI();
					log.debug("Set resources base path to |%s|.", baseURI);

					FopConfParser parser;
					try {
						parser = new FopConfParser(Classes.getResourceAsStream(FOP_CFG), baseURI);
					} catch (SAXException e) {
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

		long timestamp = new Date().getTime();

		TemplateEngine templateEngine = Classes.loadService(TemplateEngine.class);
		Template template = templateEngine.getTemplate(meta.getTemplateFile());
		String templateFO = template.serialize(model);

		log.debug("RTF template loading from disk and serialization to string last %d msec. Resulting string size is %d bytes.", new Date().getTime() - timestamp, templateFO.length());
		timestamp = new Date().getTime();

		try {
			FOUserAgent agent = fopFactory.newFOUserAgent();
			if (meta.hasProperty(PROP_PRODUCER)) {
				agent.setProducer(meta.getProperty(PROP_PRODUCER));
			}
			if (meta.hasProperty(PROP_CREATOR)) {
				agent.setCreator(meta.getProperty(PROP_CREATOR));
			}
			if (meta.hasProperty(PROP_AUTHOR)) {
				agent.setAuthor(meta.getProperty(PROP_AUTHOR));
			}
			if (meta.hasProperty(PROP_CREATON_DATE)) {
				Json json = Classes.loadService(Json.class);
				agent.setCreationDate((Date) json.parse(meta.getProperty(PROP_CREATON_DATE), Date.class));
			}
			if (meta.hasProperty(PROP_TITLE)) {
				agent.setTitle(meta.getProperty(PROP_TITLE));
			}
			if (meta.hasProperty(PROP_TARGET_RESOLUTION)) {
				agent.setTargetResolution(Integer.parseInt(meta.getProperty(PROP_TARGET_RESOLUTION)));
			}

			Fop fop = fopFactory.newFop(MimeConstants.MIME_RTF, agent, outputStream);

			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			Source source = new StreamSource(new StringReader(templateFO));
			Result destination = new SAXResult(fop.getDefaultHandler());
			transformer.transform(source, destination);

		} catch (Exception e) {
			log.error("Fail to generate RTF document. Stack trace follows. Dump on template FO on system error output.");
			log.dump(e.getMessage(), e);
			System.err.println(templateFO);
			throw new IOException(e);
		} finally {
			// very important to flush output stream otherwise generated RTF document may be incomplete, therefore invalid
			// behavior depend on content size and sometimes could generate valid document
			outputStream.flush();
		}

		log.debug("RTF transformation last %d msec.", new Date().getTime() - timestamp);
	}
}
