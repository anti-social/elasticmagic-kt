package dev.evo.elasticmagic

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlin.test.Test

class SourceTests {
    object OrderDoc : Document() {
        class UserDoc : SubDocument() {
            val id by int()
        }

        val id by long()
        val status by int()
        val total by float()
        val user by obj(::UserDoc)
    }

    @Test
    fun fastSource() {
        class User : BaseDocSource() {
            var id: Int? = null

            override fun getSource(): Map<String, Any?> {
                return mapOf(
                    "id" to id,
                )
            }

            override fun setSource(source: RawSource) {
                id = source["id"] as Int
            }
        }

        class Order(
            var id: Long? = null,
            var statuses: List<Int>? = null,
            var total: Float? = null,
            var user: User? = null,
        ) : BaseDocSource() {
            override fun getSource(): Map<String, Any?> {
                return mapOf(
                    "id" to id,
                    "status" to statuses,
                    "total" to total,
                    "user" to user?.getSource(),
                )
            }

            override fun setSource(source: RawSource) {
                id = source["id"]
                    ?.let(OrderDoc.id.type::deserialize)
                statuses = source["status"]
                    ?.let(RequiredListType(OrderDoc.status.type)::deserialize)
                total = source["total"]
                    ?.let(OrderDoc.total.type::deserialize)
                user = source["user"]
                    ?.let(SourceType(OrderDoc.user.getFieldType(), ::User)::deserialize)
            }
        }

        val order = Order()
        order.setSource(
            mapOf(
                "id" to 1,
                "status" to 0,
                "total" to 10,
                "user" to mapOf(
                    "id" to 2
                )
            )
        )
        order.id shouldBe 1
        order.statuses shouldBe listOf(0)
        order.total shouldBe 10.0F
        order.user?.id shouldBe 2
    }

    @Test
    fun sourceInheritance() {
        open class IdOrderDocSource : DocSource() {
            var id by SourceTests.OrderDoc.id.required()
        }

        class FullOrderDocSource : IdOrderDocSource() {
            var status by SourceTests.OrderDoc.status
            var total by SourceTests.OrderDoc.total
        }

        val minOrder = IdOrderDocSource()
        shouldThrow<IllegalStateException> {
            minOrder.getSource()
        }

        val fullOrder = FullOrderDocSource()
        shouldThrow<IllegalStateException> {
            fullOrder.getSource() shouldContainExactly emptyMap()
        }

        shouldThrow<IllegalArgumentException> {
            fullOrder.setSource(emptySource())
        }

        fullOrder.setSource(mapOf("id" to 1))
        fullOrder.id shouldBe 1
        fullOrder.status.shouldBeNull()
        fullOrder.total.shouldBeNull()
        fullOrder.getSource() shouldContainExactly mapOf("id" to 1L)

        fullOrder.setSource(mapOf("id" to 2, "status" to 0))
        fullOrder.id shouldBe 2
        fullOrder.status shouldBe 0
        fullOrder.total.shouldBeNull()
        fullOrder.getSource() shouldContainExactly mapOf<String, Any?>("id" to 2L, "status" to 0)

        fullOrder.setSource(mapOf("id" to 3, "total" to 9.99))
        fullOrder.id shouldBe 3
        fullOrder.status.shouldBeNull()
        fullOrder.total shouldBe 9.99F
        fullOrder.getSource() shouldContainExactly mapOf("id" to 3L, "total" to 9.99F)
    }

    @Test
    fun optionalValue() {
        class OrderDocSource : DocSource() {
            var status by SourceTests.OrderDoc.status
        }

        val order = OrderDocSource()
        order.status.shouldBeNull()
        order.getSource() shouldContainExactly emptyMap()

        order.setSource(mapOf("status" to null))
        order.status.shouldBeNull()
        order.getSource() shouldContainExactly mapOf("status"  to null)

        order.setSource(mapOf("status" to 0))
        order.status shouldBe 0
        order.getSource() shouldContainExactly mapOf("status" to 0)

        order.status = 1
        order.status shouldBe 1
        order.getSource() shouldContainExactly mapOf("status" to 1)

        order.status = null
        order.status.shouldBeNull()
        order.getSource() shouldContainExactly mapOf("status" to null)
    }

    @Test
    fun requiredValue() {
        class OrderDocSource : DocSource() {
            var status by SourceTests.OrderDoc.status.required()
        }

        shouldThrow<IllegalArgumentException> {
            OrderDocSource().setSource(emptySource())
        }

        shouldThrow<IllegalArgumentException> {
            OrderDocSource().setSource(mapOf("status" to null))
        }

        val order = OrderDocSource()
        shouldThrow<IllegalStateException> {
            order.getSource()
        }

        order.setSource(mapOf("status" to 0))
        order.status shouldBe 0
        order.getSource() shouldContainExactly mapOf("status" to 0)

        order.status = 1
        order.status shouldBe 1
        order.getSource() shouldContainExactly mapOf("status" to 1)
    }

    @Test
    fun optionalListOfOptionalValues() {
        class OrderDocSource : DocSource() {
            var status by SourceTests.OrderDoc.status.list()
        }

        val order = OrderDocSource()
        order.status.shouldBeNull()
        order.getSource() shouldContainExactly emptyMap()

        order.setSource(mapOf("status" to null))
        order.status.shouldBeNull()
        order.getSource() shouldContainExactly mapOf("status" to null)

        order.setSource(mapOf("status" to 1))
        order.status shouldBe listOf(1)
        order.getSource() shouldContainExactly mapOf("status" to listOf(1))

        order.status = emptyList()
        order.status shouldBe emptyList()
        order.getSource() shouldContainExactly mapOf("status" to emptyList<Nothing>())

        order.status = listOf(1, 2, null)
        order.status shouldBe listOf(1, 2, null)
        order.getSource() shouldContainExactly mapOf("status" to listOf(1, 2, null))

        order.setSource(mapOf("status" to listOf(null)))
        order.status shouldBe listOf(null)
        order.getSource() shouldContainExactly mapOf("status" to listOf(null))
    }

    @Test
    fun optionalListOfRequiredValues() {
        class OrderDocSource : DocSource() {
            var status by OrderDoc.status.required().list()
        }

        val order = OrderDocSource()
        order.status.shouldBeNull()
        order.getSource() shouldContainExactly emptyMap()

        order.setSource(mapOf("status" to null))
        order.status.shouldBeNull()
        order.getSource() shouldContainExactly mapOf("status" to null)

        order.status = emptyList()
        order.status shouldBe emptyList()
        order.getSource() shouldContainExactly mapOf("status" to emptyList<Nothing>())

        order.status = listOf(1, 2)
        order.status shouldBe listOf(1, 2)
        order.getSource() shouldContainExactly mapOf("status" to listOf(1, 2))

        shouldThrow<IllegalArgumentException> {
            order.setSource(mapOf("status" to listOf(null)))
        }
        order.status shouldBe null
        order.getSource() shouldContainExactly emptyMap()
    }

    @Test
    fun requiredListOfOptionalValues() {
        class OrderDocSource : DocSource() {
            var status by OrderDoc.status.list().required()
        }

        val order = OrderDocSource()
        shouldThrow<IllegalStateException> {
            order.getSource()
        }

        shouldThrow<IllegalStateException> {
            order.status
        }

        shouldThrow<IllegalArgumentException> {
            order.setSource(mapOf("status" to null))
        }
        shouldThrow<IllegalStateException> {
            order.getSource()
        }

        order.status = emptyList()
        order.status shouldBe emptyList()
        order.getSource() shouldBe mapOf("status" to emptyList<Nothing>())

        order.status = listOf(1, 2, null)
        order.status shouldBe listOf(1, 2, null)
        order.getSource() shouldBe mapOf("status" to listOf(1, 2, null))

        order.setSource(mapOf("status" to listOf(null)))
        order.status shouldBe listOf(null)
        order.getSource() shouldBe mapOf("status" to listOf(null))
    }

    @Test
    fun requiredListOfRequiredValues() {
        class OrderDocSource : DocSource() {
            var status by OrderDoc.status.required().list().required()
        }

        val order = OrderDocSource()
        shouldThrow<IllegalStateException> {
            order.getSource()
        }

        shouldThrow<IllegalStateException> {
            order.status
        }

        shouldThrow<IllegalArgumentException> {
            order.setSource(mapOf("status" to null))
        }
        shouldThrow<IllegalStateException> {
            order.getSource()
        }

        order.status = emptyList()
        order.status shouldBe emptyList()
        order.getSource() shouldBe mapOf("status" to emptyList<Nothing>())

        order.status = listOf(1, 2)
        order.status shouldBe listOf(1, 2)
        order.getSource() shouldBe mapOf("status" to listOf(1, 2))

        shouldThrow<IllegalArgumentException> {
            order.setSource(mapOf("status" to listOf(null)))
        }
        shouldThrow<IllegalStateException> {
            order.getSource()
        }

        order.setSource(mapOf("status" to listOf(2, 1)))
        order.status shouldBe listOf(2, 1)
        order.getSource() shouldBe mapOf("status" to listOf(2, 1))
    }

    @Test
    fun optionalSource() {
        class UserDocSource : DocSource() {
            var id by OrderDoc.user.id
        }

        class OrderDocSource : DocSource() {
            var user by OrderDoc.user.source(::UserDocSource)
        }

        val order = OrderDocSource()
        order.user.shouldBeNull()
        order.getSource() shouldBe emptyMap()

        order.setSource(mapOf("user" to null))
        order.user.shouldBeNull()
        order.getSource() shouldBe mapOf("user" to null)

        order.setSource(mapOf("user" to emptyMap<Nothing, Nothing>()))
        order.user.shouldNotBeNull().let { user ->
            user.id.shouldBeNull()
        }
        order.getSource() shouldBe mapOf("user" to emptyMap<Nothing, Nothing>())

        order.setSource(mapOf("user" to mapOf("id" to 1)))
        order.user.shouldNotBeNull().let { user ->
            user.id shouldBe 1
        }
        order.getSource() shouldBe mapOf("user" to mapOf("id" to 1))

        order.user = null
        order.user.shouldBeNull()
        order.getSource() shouldBe mapOf("user" to null)

        order.user = UserDocSource().apply {
            id = 19
        }
        order.user?.id shouldBe 19
        order.getSource() shouldBe mapOf("user" to mapOf("id" to 19))
    }

    @Test
    fun requiredSource() {
        class UserDocSource : DocSource() {
            var id by OrderDoc.user.id
        }

        class OrderDocSource : DocSource() {
            var user by OrderDoc.user.source(::UserDocSource).required()
        }

        val order = OrderDocSource()
        shouldThrow<IllegalStateException> {
            order.user
        }
        shouldThrow<IllegalStateException> {
            order.getSource()
        }

        shouldThrow<IllegalArgumentException> {
            order.setSource(mapOf("user" to null))
        }

        order.setSource(mapOf("user" to emptyMap<Nothing, Nothing>()))
        order.user.shouldNotBeNull().let { user ->
            user.id.shouldBeNull()
        }
        order.getSource() shouldBe mapOf("user" to emptyMap<Nothing, Nothing>())

        order.setSource(mapOf("user" to mapOf("id" to 1)))
        order.user.shouldNotBeNull().let { user ->
            user.id shouldBe 1
        }
        order.getSource() shouldContainExactly mapOf("user" to mapOf("id" to 1))

        order.user = UserDocSource().apply {
            id = 19
        }
        order.user.id shouldBe 19
        order.getSource() shouldContainExactly mapOf("user" to mapOf("id" to 19))
    }

    @Test
    fun optionalListOfOptionalSources() {
        class UserDocSource() : DocSource() {
            var id by OrderDoc.user.id

            constructor(id: Int?) : this() {
                this.id = id
            }
        }

        class OrderDocSource : DocSource() {
            var users by OrderDoc.user.source(::UserDocSource).list()
        }

        val order = OrderDocSource()
        order.users.shouldBeNull()
        order.getSource() shouldContainExactly emptyMap()

        order.setSource(mapOf("user" to null))
        order.users.shouldBeNull()
        order.getSource() shouldContainExactly mapOf("user" to null)

        order.setSource(mapOf("user" to emptyList<Nothing>()))
        order.users shouldBe emptyList()
        order.getSource() shouldContainExactly mapOf("user" to emptyList<Nothing>())

        order.setSource(mapOf("user" to listOf(null)))
        order.users shouldBe listOf(null)
        order.getSource() shouldContainExactly mapOf("user" to listOf(null))

        order.setSource(mapOf("user" to listOf(mapOf("id" to null))))
        order.users shouldBe listOf(UserDocSource(null))
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to null)))

        order.setSource(mapOf("user" to listOf(mapOf("id" to 1))))
        order.users shouldBe listOf(UserDocSource(1))
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 1)))

        order.setSource(mapOf("user" to mapOf("id" to 2)))
        order.users shouldBe listOf(UserDocSource(2))
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 2)))

        order.users = listOf(UserDocSource(id = 19))
        order.users.let { users ->
            users?.get(0)?.id shouldBe 19
        }
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 19)))
    }

    @Test
    fun optionalListOfRequiredSources() {
        class UserDocSource() : DocSource() {
            var id by OrderDoc.user.id

            constructor(id: Int?) : this() {
                this.id = id
            }
        }

        class OrderDocSource : DocSource() {
            var users by OrderDoc.user.source(::UserDocSource).required().list()
        }

        val order = OrderDocSource()
        order.users.shouldBeNull()
        order.getSource() shouldContainExactly emptyMap()

        order.setSource(mapOf("user" to null))
        order.users.shouldBeNull()
        order.getSource() shouldContainExactly mapOf("user" to null)

        order.setSource(mapOf("user" to emptyList<Nothing>()))
        order.users shouldBe emptyList()
        order.getSource() shouldContainExactly mapOf("user" to emptyList<Nothing>())

        shouldThrow<IllegalArgumentException> {
            order.setSource(mapOf("user" to listOf(null)))
        }
        order.getSource() shouldContainExactly emptyMap()

        order.setSource(mapOf("user" to listOf(mapOf("id" to null))))
        order.users shouldBe listOf(UserDocSource(null))
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to null)))

        order.setSource(mapOf("user" to listOf(mapOf("id" to 1))))
        order.users shouldBe listOf(UserDocSource(1))
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 1)))

        order.setSource(mapOf("user" to mapOf("id" to 2)))
        order.users shouldBe listOf(UserDocSource(2))
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 2)))

        order.users = listOf(UserDocSource(id = 19))
        order.users.let { users ->
            users?.get(0)?.id shouldBe 19
        }
        order.getSource() shouldBe mapOf("user" to listOf(mapOf("id" to 19)))
    }

    @Test
    fun requiredListOfOptionalSources() {
        class UserDocSource() : DocSource() {
            var id by OrderDoc.user.id

            constructor(id: Int?) : this() {
                this.id = id
            }
        }

        class OrderDocSource : DocSource() {
            var users by OrderDoc.user.source(::UserDocSource).list().required()
        }

        val order = OrderDocSource()
        shouldThrow<IllegalStateException> {
            order.users
        }
        shouldThrow<IllegalStateException> {
            order.getSource()
        }

        shouldThrow<IllegalArgumentException> {
            order.setSource(mapOf("user" to null))
        }

        order.setSource(mapOf("user" to emptyList<Nothing>()))
        order.users shouldBe emptyList()
        order.getSource() shouldContainExactly mapOf("user" to emptyList<Nothing>())

        order.setSource(mapOf("user" to listOf(null)))
        order.users shouldBe listOf(null)
        order.getSource() shouldContainExactly mapOf("user" to listOf(null))

        order.setSource(mapOf("user" to listOf(mapOf("id" to null))))
        order.users shouldBe listOf(UserDocSource(null))
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to null)))

        order.setSource(mapOf("user" to listOf(mapOf("id" to 1))))
        order.users shouldBe listOf(UserDocSource(1))
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 1)))

        order.setSource(mapOf("user" to mapOf("id" to 2)))
        order.users shouldBe listOf(UserDocSource(2))
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 2)))

        order.users = listOf(UserDocSource(id = 19))
        order.users.let { users ->
            users[0]?.id shouldBe 19
        }
        order.getSource() shouldContainExactly  mapOf("user" to listOf(mapOf("id" to 19)))
    }

    @Test
    fun requiredListOfRequiredSources() {
        class UserDocSource() : DocSource() {
            var id by OrderDoc.user.id

            constructor(id: Int?) : this() {
                this.id = id
            }
        }

        class OrderDocSource : DocSource() {
            var users by OrderDoc.user.source(::UserDocSource).required().list().required()
        }

        val order = OrderDocSource()
        shouldThrow<IllegalStateException> {
            order.users
        }
        shouldThrow<IllegalStateException> {
            order.getSource()
        }

        shouldThrow<IllegalArgumentException> {
            order.setSource(mapOf("user" to null))
        }

        order.setSource(mapOf("user" to emptyList<Nothing>()))
        order.users shouldBe emptyList()
        order.getSource() shouldContainExactly mapOf("user" to emptyList<Nothing>())

        shouldThrow<IllegalArgumentException> {
            order.setSource(mapOf("user" to listOf(null)))
        }
        shouldThrow<IllegalStateException> {
            order.getSource()
        }

        order.setSource(mapOf("user" to listOf(mapOf("id" to null))))
        order.users shouldBe listOf(UserDocSource(null))
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to null)))

        order.setSource(mapOf("user" to listOf(mapOf("id" to 1))))
        order.users shouldBe listOf(UserDocSource(1))
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 1)))

        order.setSource(mapOf("user" to mapOf("id" to 2)))
        order.users shouldBe listOf(UserDocSource(2))
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 2)))

        order.users = listOf(UserDocSource(id = 19))
        order.users.let { users ->
            users[0].id shouldBe 19
        }
        order.getSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 19)))
    }
}
