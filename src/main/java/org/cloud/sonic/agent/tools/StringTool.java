package org.cloud.sonic.agent.tools;

import com.ibm.icu.text.RuleBasedNumberFormat;
import java.util.Locale;

public class StringTool {
	public static String ordinal(final int i) {
		return ordinal(i, new Locale("en", "US", ""));
	} // end ordinal()
	
	public static String ordinal(final int i, final Locale l) { // https://android.googlesource.com/platform/external/icu/+/refs/heads/o-iot-preview-5/android_icu4j/src/main/tests/android/icu/dev/test/format/RbnfTest.java#217
		final RuleBasedNumberFormat formatter = new RuleBasedNumberFormat(l, RuleBasedNumberFormat.ORDINAL);
		return formatter.format(i);
	} // end ordinal()
} // end class
