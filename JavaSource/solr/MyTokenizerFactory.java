package solr;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.UnicodeWhitespaceTokenizer;
import org.apache.lucene.analysis.util.TokenizerFactory;
import org.apache.lucene.util.AttributeFactory;

public class MyTokenizerFactory extends TokenizerFactory {
	  public static final String RULE_JAVA = "java";
	  public static final String RULE_UNICODE = "unicode";
	  private static final Collection<String> RULE_NAMES = Arrays.asList(RULE_JAVA, RULE_UNICODE);

	  private final String rule;

	  public MyTokenizerFactory(Map<String,String> args) {
	    super(args);

	    rule = get(args, "rule", RULE_NAMES, RULE_JAVA);

	    if (!args.isEmpty()) {
	      throw new IllegalArgumentException("Unknown parameters: " + args);
	    }
	  }

	  @Override
	  public Tokenizer create(AttributeFactory factory) {
	    switch (rule) {
	      case RULE_JAVA:
	        return new MyTokenizer(factory);
	      case RULE_UNICODE:
	        return new UnicodeWhitespaceTokenizer(factory);
	      default:
	        throw new AssertionError();
	    }
	  }
}
