package js.fop;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import js.tiny.container.mvc.AbstractView;
import js.tiny.container.mvc.View;
import js.tiny.container.mvc.ViewMeta;
import js.util.Classes;

public class ViewUnitTest
{
  private MockHttpServletResponse httpResponse;

  @Before
  public void beforeTest() throws Exception
  {
    httpResponse = new MockHttpServletResponse();
  }

  @Test
  public void testXconf()
  {

  }

  @Test
  public void testPdfView() throws Exception
  {
    httpResponse.setTargetFile(new File("fixture/page.pdf"));
    
    View view = new PdfView();
    setViewMeta(view, PdfView.class);
    view.setModel(getPerson());
    view.serialize(httpResponse);

    assertEquals("application/pdf", httpResponse.getHeader("Content-Type"));
    assertHeaders(httpResponse);
  }

  @Test
  public void testRtfView() throws Exception
  {
    httpResponse.setTargetFile(new File("fixture/page.rtf"));
    
    View view = new RtfView();
    setViewMeta(view, RtfView.class);
    view.setModel(getPerson());
    view.serialize(httpResponse);

    assertEquals("application/rtf", httpResponse.getHeader("Content-Type"));
    assertHeaders(httpResponse);
  }

  private static void assertHeaders(HttpServletResponse httpResponse) {
    assertEquals("no-cache;no-store", httpResponse.getHeader("Cache-Control"));
    assertEquals("no-cache", httpResponse.getHeader("Pragma"));
    assertEquals("Thu, 01 Jan 1970 02:00:00 EET", httpResponse.getHeader("Expires"));
  }
  
  private static void setViewMeta(View view, Class<? extends View> type) throws Exception
  {
    Properties properties = new Properties();
    properties.setProperty("font-base-url", "C:/Windows/Fonts");
    Classes.invoke(view, AbstractView.class, "setMeta", new ViewMeta(new File("fixture/page.fo"), type, properties));
  }

  private static Person getPerson() throws Exception
  {
    Person person = new Person(true);
    return person;
  }

  // ------------------------------------------------------
  // FIXTURE

  private static class MockHttpServletResponse extends HttpServletResponseStub
  {
    private DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
    private Map<String, String> headers = new HashMap<String, String>();
    private File targetFile;

    public void setTargetFile(File targetFile)
    {
      this.targetFile = targetFile;
    }

    @Override
    public void setContentType(String contentType)
    {
      setHeader("Content-Type", contentType);
    }

    @Override
    public void setHeader(String header, String value)
    {
      if(headers.containsKey(header)) {
        throw new IllegalStateException("Set existing header");
      }
      headers.put(header, value);
    }

    @Override
    public void setDateHeader(String header, long value)
    {
      Date date = new Date(value);
      setHeader(header, this.df.format(date));
    }

    @Override
    public void addHeader(String header, String value)
    {
      String existingValue = headers.get(header);
      if(existingValue == null) {
        throw new IllegalStateException("Add to an not existing header.");
      }
      headers.put(header, existingValue + ";" + value);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException
    {
      return new MockServletOutputStream(targetFile);
    }

    public String getHeader(String header)
    {
      return headers.get(header);
    }
  }
}
