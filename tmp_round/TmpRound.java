import cn.hutool.json.JSONUtil;

import java.time.LocalDateTime;

public class TmpRound {
    public static class Shop {
        private Long id;
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class RD {
        private LocalDateTime expire;
        private Object data;

        public LocalDateTime getExpire() {
            return expire;
        }

        public void setExpire(LocalDateTime expire) {
            this.expire = expire;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    public static void main(String[] args) {
        Shop s = new Shop();
        s.setId(1L);
        s.setName("abc");
        RD rd = new RD();
        rd.setData(s);
        rd.setExpire(LocalDateTime.now().plusSeconds(1800));
        String json = JSONUtil.toJsonStr(rd);
        System.out.println("stored = " + json);
        RD back = JSONUtil.toBean(json, RD.class);
        System.out.println("dataObj type = " + back.getData().getClass() + " , toString = " + back.getData().toString());
        Shop s2 = JSONUtil.toBean(back.getData().toString(), Shop.class);
        System.out.println("shop = " + s2.getId() + "," + s2.getName() + " , expire=" + back.getExpire());
        // 旧格式（普通 shop json）解析为 RD 时的字段缺失情况
        RD legacy = JSONUtil.toBean("{\"id\":1,\"name\":\"abc\"}", RD.class);
        System.out.println("legacy expire = " + legacy.getExpire() + " , data = " + legacy.getData());
    }
}
