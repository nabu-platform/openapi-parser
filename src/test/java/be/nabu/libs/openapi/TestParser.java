package be.nabu.libs.openapi;

import java.io.IOException;
import java.io.InputStream;

import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.swagger.api.SwaggerDefinition;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import junit.framework.TestCase;

public class TestParser extends TestCase {
//	public void testDuplicateDefinitionElement() throws IOException {
//		try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream("test-duplicate-definition-element.json")) {
//			SwaggerDefinition definition = new OpenApiParserv3().parse("test", input);
//			System.out.println("Namespaces: " + definition.getRegistry().getNamespaces());
//			for (ComplexType type : definition.getRegistry().getComplexTypes("test.types")) {
//				System.out.println("Found: " + type);
//			}
//			ComplexType complexType = definition.getRegistry().getComplexType("test.types", "image");
//			for (Element<?> element : TypeUtils.getAllChildren(complexType)) {
//				System.out.println(element.getName() + ": " + element.getType().getName());
//			}
//		}
//	}
	
	public void testArrayAllOfExtension() throws IOException {
		try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream("test-array-allof.json")) {
			SwaggerDefinition definition = new OpenApiParserv3().parse("test", input);
			ComplexType openingHours = definition.getRegistry().getComplexType("test.types", "eventOpeningHours");
			assertNotNull(openingHours);
			assertNotNull(openingHours.get("opens"));
			assertNotNull(openingHours.get("childcare"));
			assertNotNull(((ComplexType) openingHours.get("childcare").getType()).get("start"));
			assertEquals(Integer.valueOf(0), ValueUtils.getValue(MaxOccursProperty.getInstance(), openingHours.getProperties()));

			ComplexType event = definition.getRegistry().getComplexType("test.types", "event");
			assertNotNull(event);
			Element<?> faqs = event.get("faqs");
			assertNotNull(faqs);
			assertTrue(faqs.getType() instanceof ComplexType);
			ComplexType faq = definition.getRegistry().getComplexType("test.types", "eventFaq");
			assertNotNull(faq);
			assertSame(faq, faqs.getType().getSuperType());
			assertNotNull(((ComplexType) faqs.getType()).get("nl"));
			assertNotNull(((ComplexType) ((ComplexType) faqs.getType()).get("nl").getType()).get("question"));
			assertNotNull(((ComplexType) ((ComplexType) faqs.getType()).get("nl").getType()).get("answer"));
			assertEquals(Integer.valueOf(30), ValueUtils.getValue(MaxOccursProperty.getInstance(), faqs.getType().getProperties()));
		}
	}

	public void testAnnotatedReferenceAllOf() throws IOException {
		try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream("test-annotated-reference-allof.json")) {
			SwaggerDefinition definition = new OpenApiParserv3().parse("test", input);
			ComplexType childcare = definition.getRegistry().getComplexType("test.types", "eventOpeningHoursChildcare");
			assertNotNull(childcare);
			Element<?> start = childcare.get("start");
			assertNotNull(start);
			assertTrue(start.getType() instanceof SimpleType);
			assertEquals(String.class, ((SimpleType<?>) start.getType()).getInstanceClass());

			Element<?> constrained = childcare.get("constrained");
			assertNotNull(constrained);
			assertTrue(constrained.getType() instanceof ComplexType);
		}
	}

	public void testAnyOfNullableVariants() throws IOException {
		try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream("test-anyof-nullable.json")) {
			SwaggerDefinition definition = new OpenApiParserv3().parse("test", input);
			ComplexType nullableObject = definition.getRegistry().getComplexType("test.types", "nullableObject");
			assertNotNull(nullableObject);
			assertNull(nullableObject.getSuperType());
			assertNotNull(nullableObject.get("name"));
			assertNull(nullableObject.get("value"));
			assertEquals(Integer.valueOf(1), ValueUtils.getValue(MinOccursProperty.getInstance(), nullableObject.get("name").getProperties()));
			
			ComplexType baseObject = definition.getRegistry().getComplexType("test.types", "baseObject");
			assertNotNull(baseObject);
			assertNotNull(baseObject.get("value"));
			assertNull(baseObject.get("name"));
			assertEquals(Integer.valueOf(1), ValueUtils.getValue(MinOccursProperty.getInstance(), baseObject.get("value").getProperties()));
		}
	}
}
