package net.osmand;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import org.apache.commons.logging.Log;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;
import com.ibm.icu.text.BidiRun;

public class Reshaper {
	private final static Log LOG = PlatformUtil.getLog(Reshaper.class);
	
	public static String reshape(byte[] bytes) {
		try {
			return reshape(new String(bytes, "UTF-8"), false);
		} catch (UnsupportedEncodingException e) {
			return "";
		}
	}
	
	public static String reshape(String s) {
		return reshape(s, true);
	}

	public static String reshape(String s, boolean reshape) {
		if (reshape) {
			ArabicShaping as = new ArabicShaping(ArabicShaping.LETTERS_SHAPE |
					ArabicShaping.LENGTH_GROW_SHRINK);
			//printSplit("B", s);
			try {
				s = as.shape(s);
			} catch (ArabicShapingException e) {
				LOG.error(e.getMessage(), e);
			}
			//printSplit("A", s);
		}
		s = bidiShape(s, reshape);

		return s;
	}

	public static String bidiShape(String s, boolean mirror) {
		String originalS = s;
		try {

			Bidi line = new Bidi(s.length(), s.length());
			line.setPara(s,  Bidi.LEVEL_DEFAULT_LTR, null);
//			line.setPara(s, Bidi.LEVEL_DEFAULT_LTR, null);
//			s = line.writeReordered(Bidi.DO_MIRRORING);
//			s = reordered;
			byte direction = line.getDirection();
	        if (direction != Bidi.MIXED) {
	            // unidirectional
				if (line.isLeftToRight() || !mirror) {
					return s;
				} else {
	        		char[] chs = new char[s.length()];
	        		for(int i = 0; i< chs.length ; i++) {
//	        			chs[i] = s.charAt(chs.length - i - 1);
	        			chs[i] = mirror(s.charAt(chs.length - i - 1));
	        		}
	        		return new String(chs);
	        	}
			} else {
				// mixed-directional
//				System.out.println(s);
//				printSplit("Split", s);
				int count = line.countRuns();
				StringBuilder res = new StringBuilder();
				// iterate over both directional and style runs
				for (int i = 0; i < count; ++i) {
					StringBuilder runs = new StringBuilder();
					BidiRun run = line.getVisualRun(i);
					boolean ltr = run.getDirection() == Bidi.LTR;
					int start = run.getStart();
					int limit = run.getLimit();
					int begin = ltr ? start : limit - 1;
					int end = ltr ? limit : start - 1;
//					int end = ltr ? start - 1: limit;
//					int begin = ltr ? limit - 1 : start;
					int ind = begin;

					while (ind != end) {
						char ch = s.charAt(ind);
						if (!ltr) { // !
							ch = mirror(ch);
						}
						res.append(ch);
						runs.append(ch);
						if (ltr) { // !
							ind++;
						} else {
							ind--;
						}
						
					}
					//printSplit(run.getDirection() + " " + run.getEmbeddingLevel(), runs.toString());
				}
				if (!mirror) {
					res.reverse();
				}
				//printSplit("Split", res.reverse().toString());
				return res.toString();
			}
		} catch (RuntimeException e) {
			LOG.error(e.getMessage(), e);
			return s;
		}

	}
	private static char mirror(char ch) {
		switch (ch) { 
			case '(': ch = ')'; break;
			case ')': ch = '('; break;
			case '[': ch = ']'; break;
			case ']': ch = '['; break;
		}
		return ch;
	}
	public static void main(String[] args) {
		test2();
		test3();
		test4();
		test5();
	}
	
	public static void test3() {
		String s = "מרכז מסחרי/השלום (40050)";
		String reshape = reshape(s);
		String expected = "(40050) םולשה/ירחסמ זכרמ";
		check(s, reshape, expected);
	}
	
	public static void test5() {
		String s = "מרכז מסחרי/השלום (מרז)";
		String reshape = reshape(s);
		String expected = "(זרמ) םולשה/ירחסמ זכרמ";
		check(s, reshape, expected);
	}
	
	public static void check(String source, String reshape, String expected) {
		printSplit("Source  ", source);
		printSplit("Expected", expected);
		printSplit("Reshaped", reshape);
		System.out.println(reshape);
		if (!reshape.equals(expected)) {
			throw new IllegalArgumentException(String.format("Bug: expected '%s', reshaped '%s'", expected, reshape));
		}
	}
	
	static void printSplit(String p, String source) {
		printSplit(p, source, true);
		printSplit(p, source, false);
	}
	static void printSplit(String p, String source, boolean f) {
		System.out.print(p);
		System.out.print(": \u2066");
		for (int i = 0; i < source.length(); i++) {
			if (f) {
				System.out.print(source.charAt(i));
				System.out.print(" \u200e");
			} else {
				System.out.print(String.format("%04x ", (int) source.charAt(i)));
			}
		}
//		System.out.println(Arrays.toString(source.toCharArray()));
		System.out.println();
		System.out.flush();
	}

	public static void test2() {
		String s = "گچ پژ نمکی باللغة العربي";
		String reshape = reshape(s);
		String expected1 = "ﻲﺑﺮﻌﻟﺍ ﺔﻐﻠﻟﺎﺑ ﯽﮑﻤﻧ ﮋﭘ ﭻﮔ";
		String expected2 = "ﻲﺑﺮﻌﻟﺍ ﺔﻐﻠﻟﺎﺑ یکﻤﻧ ژپ چگ";
		check(s, reshape, expected1);
	}
	
	public static void test4() {
		String s = "Abc (123)";
		check(s, reshape(s), s);
	}

}