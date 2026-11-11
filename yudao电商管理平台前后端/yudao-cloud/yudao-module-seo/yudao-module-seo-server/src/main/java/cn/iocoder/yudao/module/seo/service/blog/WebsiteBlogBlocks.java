package cn.iocoder.yudao.module.seo.service.blog;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.BLOG_ARTICLE_INVALID;

/** Structured text/image blocks; raw HTML and executable embeds are not accepted. */
public final class WebsiteBlogBlocks {
    private WebsiteBlogBlocks() {}
    public static List<JsonNode> validate(List<JsonNode> blocks) {
        if(blocks==null)return List.of();
        if(blocks.size()>500)fail();
        for(JsonNode b:blocks) {
            if(!b.isObject())fail();
            String type=b.path("type").asText();
            Set<String> allowed=switch(type) {
                case "paragraph" -> Set.of("type","text");
                case "heading" -> Set.of("type","text","level");
                case "list" -> Set.of("type","items","ordered","table");
                case "image" -> Set.of("type","src","alt");
                default -> throw exception(BLOG_ARTICLE_INVALID);
            };
            b.fieldNames().forEachRemaining(f->{if(!allowed.contains(f))fail();});
            if(type.equals("paragraph")||type.equals("heading"))text(b.path("text"),16000,true);
            if(type.equals("heading")&&(!b.path("level").isInt()||b.path("level").asInt()<2||b.path("level").asInt()>6))fail();
            if(type.equals("list")) { for(String flag:Set.of("ordered","table"))if(b.has(flag)&&!b.path(flag).isBoolean())fail(); JsonNode items=b.path("items");if(!items.isArray()||items.size()==0||items.size()>100)fail();for(JsonNode item:items)text(item,8000,true); }
            if(type.equals("image")) {
                String src=text(b.path("src"),500,true);text(b.path("alt"),240,false);
                try { cn.iocoder.yudao.module.seo.service.page.TablewareCatalogContentValidator.asset(src); }
                catch(cn.iocoder.yudao.framework.common.exception.ServiceException error){fail();}
            }
        }
        return blocks.stream().<JsonNode>map(JsonNode::deepCopy).toList();
    }
    private static String text(JsonNode n,int max,boolean required) {if(!n.isTextual()||n.asText().length()>max||(required&&n.asText().isBlank())||n.asText().chars().anyMatch(c->c<32&&c!=10&&c!=13&&c!=9))fail();return n.asText();}
    private static void fail(){throw exception(BLOG_ARTICLE_INVALID);}
}
