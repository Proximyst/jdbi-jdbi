/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.vavr;

import io.vavr.Tuple1;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.Tuple4;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Something;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.NoSuchMapperException;
import org.jdbi.v3.core.mapper.SomethingMapper;
import org.jdbi.v3.testing.junit5.JdbiExtension;
import org.jdbi.v3.testing.junit5.internal.TestingInitializers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestVavrTupleRowMapperFactoryWithDB {

    @RegisterExtension
    public JdbiExtension h2Extension = JdbiExtension.h2().withInitializer(TestingInitializers.something()).installPlugins();

    @BeforeEach
    public void addData() {
        Handle handle = h2Extension.openHandle();
        handle.createUpdate("insert into something (id, name, integerValue, intValue) values (1, 'eric', 99, 100)").execute();
        handle.createUpdate("insert into something (id, name, integerValue, intValue) values (2, 'brian', 101, 102)").execute();
    }

    @Test
    public void testMapTuple1WithNameOnlyUsingColumnMapperShouldSucceed() {
        Tuple1<String> result = h2Extension.getSharedHandle()
            .createQuery("select name from something where id = 1")
            .mapTo(new GenericType<Tuple1<String>>() {})
            .one();

        assertThat(result._1).isEqualToIgnoringCase("eric");
    }

    @Test
    public void testMapTuple1UsingRegisteredRowMapperShouldSucceed() {
        Handle handle = h2Extension.getSharedHandle();
        handle.registerRowMapper(new SomethingMapper());

        Tuple1<Something> result = handle
                .createQuery("select id, name from something where id = 1")
                .mapTo(new GenericType<Tuple1<Something>>() {})
                .one();

        assertThat(result._1).isEqualTo(new Something(1, "eric"));
    }

    @Test
    public void testMapTuple2UsingRegisteredRowMappersShouldSucceed() {
        Handle handle = h2Extension.getSharedHandle();
        handle.registerRowMapper(new SomethingMapper());
        handle.registerRowMapper(SomethingValues.class,
                (rs, ctx) -> new SomethingValues(rs.getInt("integerValue"),
                        rs.getInt("intValue")));

        Tuple2<Something, SomethingValues> result = handle
                .createQuery("select * from something where id = 2")
                .mapTo(new GenericType<Tuple2<Something, SomethingValues>>() {})
                .one();

        assertThat(result._1).isEqualTo(new Something(2, "brian"));
        assertThat(result._2).isEqualTo(new SomethingValues(101, 102));
    }

    @Test
    public void testMapTuple2HavingOnlyOneRowMapperShouldFail() {
        final Handle handle = h2Extension.getSharedHandle();
        handle.registerRowMapper(new SomethingMapper());

        assertThatThrownBy(() -> handle
                .createQuery("select * from something where id = 1")
                .mapTo(new GenericType<Tuple2<Something, SomethingValues>>() {})
                .one()
       ).isInstanceOf(NoSuchMapperException.class)
                .hasMessageContaining("SomethingValues");
    }

    @Test
    public void testMapTuple3WithExtraSpecifiedColumnShouldSucceed() {
        Handle handle = h2Extension.getSharedHandle();
        handle.registerRowMapper(new SomethingMapper());
        handle.configure(TupleMappers.class, c ->
                c.setColumn(2, "integerValue").setColumn(3, "intValue"));

        Tuple3<Something, Integer, Integer> result = handle
                .createQuery("select * from something where id = 1")
                .mapTo(new GenericType<Tuple3<Something, Integer, Integer>>() {})
                .one();

        assertThat(result._1).isEqualTo(new Something(1, "eric"));
        assertThat(result._2).isEqualTo(99);
        assertThat(result._3).isEqualTo(100);
    }

    @Test
    public void testMapTuple3WithAllSpecifiedColumnsShouldRespectConfiguration() {
        Handle handle = h2Extension.getSharedHandle();
        handle.configure(TupleMappers.class, c ->
                c.setColumn(1, "integerValue")
                        .setColumn(2, "intValue")
                        .setColumn(3, "id"));

        Tuple3<Integer, Integer, Integer> result = handle
                .createQuery("select * from something where id = 1")
                .mapTo(new GenericType<Tuple3<Integer, Integer, Integer>>() {})
                .one();

        assertThat(result._1).isEqualTo(99);
        assertThat(result._2).isEqualTo(100);
        assertThat(result._3).isOne();
    }

    @Test
    public void testMapTuple3WithoutSpecifiedColumnShouldFail() {
        Handle handle = h2Extension.getSharedHandle();
        handle.registerRowMapper(new SomethingMapper());

        assertThatThrownBy(() -> handle
                .createQuery("select * from something where id = 1")
                .mapTo(new GenericType<Tuple3<Integer, Something, Integer>>() {})
                .one())
                .isInstanceOf(NoSuchMapperException.class)
                .hasMessageContaining("TupleMappers config class");
    }

    @Test
    public void testMapTuple3WithOnlyOneSpecifiedColumnShouldFail() {
        Handle handle = h2Extension.getSharedHandle();
        handle.registerRowMapper(new SomethingMapper());
        handle.configure(TupleMappers.class, c ->
                c.setColumn(1, "integerValue"));

        assertThatThrownBy(() -> handle
                .createQuery("select * from something where id = 1")
                .mapTo(new GenericType<Tuple3<Integer, Something, Integer>>() {})
                .one()).isInstanceOf(NoSuchMapperException.class)
                .isInstanceOf(NoSuchMapperException.class)
                .hasMessageContaining("TupleMappers config class");
    }

    @Test
    public void testMapTuple2SelectedColumnsShouldSucceed() {
        Tuple2<Integer, String> result = h2Extension.getSharedHandle()
            .createQuery("select intValue, name from something where id = 2")
            .mapTo(new GenericType<Tuple2<Integer, String>>() {})
            .one();

        assertThat(result._1).isEqualTo(102);
        assertThat(result._2).isEqualTo("brian");
    }

    @Test
    public void testMapTuple4AllColumnsShouldSucceed() {
        Tuple4<Integer, String, Integer, Integer> result = h2Extension.getSharedHandle()
            .createQuery("select * from something where id = 2")
            .mapTo(new GenericType<Tuple4<Integer, String, Integer, Integer>>() {})
            .one();

        assertThat(result._1).isEqualTo(2);
        assertThat(result._2).isEqualTo("brian");
        assertThat(result._3).isEqualTo(101);
        assertThat(result._4).isEqualTo(102);
    }

    private static class SomethingValues {
        private int integerValue;
        private int intValue;

        SomethingValues(int integerValue, int intValue) {
            this.integerValue = integerValue;
            this.intValue = intValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SomethingValues that = (SomethingValues) o;

            if (integerValue != that.integerValue) {
                return false;
            }
            return intValue == that.intValue;
        }

        @Override
        public int hashCode() {
            int result = integerValue;
            result = 31 * result + intValue;
            return result;
        }
    }

}
