package com.jslib.fop;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;

import com.jslib.format.StandardDateTime;

public final class Person {
	public int id;
	public String name;
	public String surname;
	public URL webPage;
	public String landline;
	public String mobile;
	public String emailAddr;
	public Date birthday;
	public State state = State.NONE;

	public Person() {
	}

	public Person(boolean initialize) throws MalformedURLException, ParseException {
		if (!initialize) {
			return;
		}
		id = 1964;
		name = "John";
		surname = "Doe";
		webPage = new URL("http://site.com/");
		landline = "0232555666";
		mobile = "0721555666";
		emailAddr = "john.doe@email.com";
		birthday = (Date) new StandardDateTime().parse("1964-03-15 13:40:00");
		state = State.ACTIVE;
	}
}
