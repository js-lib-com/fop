package js.fop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import js.io.WriterOutputStream;

public class MockServletOutputStream extends ServletOutputStream {
	private OutputStream stream;

	public MockServletOutputStream() {
		this(new ByteArrayOutputStream());
	}

	public MockServletOutputStream(File file) throws FileNotFoundException {
		this(new FileOutputStream(file));
	}

	public MockServletOutputStream(Writer writer) {
		this(new WriterOutputStream(writer));
	}

	public MockServletOutputStream(OutputStream stream) {
		this.stream = stream;
	}

	@Override
	public void write(int b) throws IOException {
		stream.write(b);
	}

	@Override
	public void flush() throws IOException {
		stream.flush();
	}

	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public void setWriteListener(WriteListener writeListener) {
	}
}