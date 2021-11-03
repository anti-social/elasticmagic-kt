package dev.evo.elasticmagic.doc

import dev.evo.elasticmagic.types.RequiredListType
import dev.evo.elasticmagic.types.SourceType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

import kotlin.test.Test

class DocSourceTests {
    object OrderDoc : Document() {
        class UserOpinionDoc(field: DocSourceField) : SubDocument(field) {
            val rating by float("rank")
        }

        class UserDoc(field: DocSourceField) : SubDocument(field) {
            val id by int()
            val opinion by obj(::UserOpinionDoc)
        }

        val id by long()
        val status by int()
        val total by float()
        val user by obj(::UserDoc)
    }

    @Test
    fun dynDocSource() {
        // TODO: Split into multiple test cases
        val order = DynDocSource()
        order.toSource() shouldContainExactly emptyMap()

        order["id"] = 1L
        order.toSource() shouldContainExactly mapOf("id" to 1L)
        order["id"] shouldBe 1L

        order[OrderDoc.id] = 2L
        order[OrderDoc.status] = 0
        order.toSource() shouldContainExactly mapOf<String, Any>(
            "id" to 2L,
            "status" to 0,
        )

        order[OrderDoc.user.id] = 111
        order.toSource() shouldContainExactly mapOf(
            "id" to 2L,
            "status" to 0,
            "user" to mapOf(
                "id" to 111
            )
        )
        val user = order[OrderDoc.user]
        val userId = user?.get(OrderDoc.user.id)
        userId shouldBe 111

        order.fromSource(mapOf(
            "id" to 3L,
            "status" to 0,
            "total" to 9.99,
            "user" to mapOf(
                "id" to 123,
                "opinion" to mapOf(
                    "rank" to 50
                )
            )
        ))
        order[OrderDoc.id] shouldBe 3L
        order["id"] shouldBe 3L
        order[OrderDoc.status] shouldBe 0
        order["status"] shouldBe 0
        order[OrderDoc.total] shouldBe 9.99F
        order["total"] shouldBe 9.99
        order[OrderDoc.user.id] shouldBe 123
        order["user.id"] shouldBe 123
        order[OrderDoc.user].let { user ->
            user.shouldNotBeNull()
            user[OrderDoc.user.id] shouldBe 123
            user["id"] shouldBe 123
            user[OrderDoc.user.opinion].let { opinion ->
                opinion.shouldNotBeNull()
                opinion[OrderDoc.user.opinion.rating] shouldBe 50.0F
                opinion["rank"] shouldBe 50
            }
        }
        order[OrderDoc.user.opinion.rating] shouldBe 50.0F
        order["user.opinion.rank"] shouldBe 50
        order.toSource() shouldContainExactly mapOf(
            "id" to 3L,
            "status" to 0,
            "total" to 9.99,
            "user" to mapOf(
                "id" to 123,
                "opinion" to mapOf(
                    "rank" to 50
                )
            )
        )

        order.fromSource(mapOf("status" to listOf(0, 1)))
        order[OrderDoc.status.list()] shouldBe listOf(0, 1)
        order.toSource() shouldBe mapOf("status" to listOf(0, 1))

        order.fromSource(mapOf("status" to listOf(null, 0, 1)))
        order[OrderDoc.status.list()] shouldBe listOf(null, 0, 1)
        order.toSource() shouldBe mapOf("status" to listOf(null, 0, 1))

        order.fromSource(mapOf("status" to 0))
        order[OrderDoc.status.list()] shouldBe listOf(0)
        order.toSource() shouldBe mapOf("status" to 0)

        order.fromSource(mapOf("user" to listOf(mapOf("id" to 111), mapOf("id" to 222))))
        order[OrderDoc.user.list()].let { users ->
            users.shouldNotBeNull()
            users[0].shouldNotBeNull()[OrderDoc.user.id] shouldBe 111
            users[1].shouldNotBeNull()[OrderDoc.user.id] shouldBe 222
        }
        order.toSource() shouldBe mapOf("user" to listOf(mapOf("id" to 111), mapOf("id" to 222)))

        DynDocSource {
            val user = DynDocSource()
            it[OrderDoc.user] = user
            it.toSource() shouldContainExactly mapOf("user" to emptyMap<String, Any>())

            user[OrderDoc.user.id] = 321
            it.toSource() shouldContainExactly mapOf("user" to mapOf("id" to 321))

            shouldThrow<IllegalArgumentException> {
                it[OrderDoc.user.opinion] = user
                Unit
            }

            it[OrderDoc.user.opinion].shouldBeNull()
        }

        DynDocSource {
            it[OrderDoc.id] = 1L
            it[OrderDoc.user] = DynDocSource {
                it[OrderDoc.user.id] = 111
                it[OrderDoc.user.opinion] = DynDocSource {
                    it[OrderDoc.user.opinion.rating] = 99F
                }
            }

            it["id"] shouldBe 1L
            it["user.id"] shouldBe 111
            it["user"].let { user ->
                user.shouldBeInstanceOf<DynDocSource>()["id"] shouldBe 111
            }

            it.toSource() shouldContainExactly mapOf(
                "id" to 1L,
                "user" to mapOf(
                    "id" to 111,
                    "opinion" to mapOf(
                        "rank" to 99F
                    )
                )
            )
        }

        DynDocSource {
            it[OrderDoc.id] = 2L

            // here opinion and user doc sources have not prefix yet
            val newOpinion = DynDocSource {
                it[OrderDoc.user.opinion.rating] = 89.9F
            }
            newOpinion.toSource() shouldContainExactly mapOf(
                "user" to mapOf(
                    "opinion" to mapOf("rank" to 89.9F)
                )
            )
            val newUser = DynDocSource {
                it[OrderDoc.user.id] = 222
            }
            newUser.toSource() shouldContainExactly mapOf("user" to mapOf("id" to 222))

            // opinion should be stripped
            newUser[OrderDoc.user.opinion] = newOpinion
            newOpinion.toSource() shouldContainExactly mapOf("rank" to 89.9F)
            newUser.toSource() shouldContainExactly mapOf(
                "user" to mapOf(
                    "id" to 222,
                    "opinion" to mapOf("rank" to 89.9F)
                )
            )

            // and user also should be stripped
            it[OrderDoc.user] = newUser
            newUser.toSource() shouldContainExactly mapOf(
                "id" to 222,
                "opinion" to mapOf("rank" to 89.9F)
            )
            it.toSource() shouldContainExactly mapOf(
                "id" to 2L,
                "user" to mapOf(
                    "id" to 222,
                    "opinion" to mapOf("rank" to 89.9F)
                )
            )
        }
    }

    @Test
    fun fastSource() {
        class User : BaseDocSource() {
            var id: Int? = null

            override fun toSource(): Map<String, Any?> {
                return mapOf(
                    "id" to id,
                )
            }

            override fun fromSource(rawSource: RawSource) {
                id = rawSource["id"]?.let(OrderDoc.user.id.getFieldType()::deserialize)
            }
        }

        class Order(
            var id: Long? = null,
            var statuses: List<Int>? = null,
            var total: Float? = null,
            var user: User? = null,
        ) : BaseDocSource() {
            override fun toSource(): Map<String, Any?> {
                return mapOf(
                    "id" to id,
                    "status" to statuses,
                    "total" to total,
                    "user" to user?.toSource(),
                )
            }

            override fun fromSource(rawSource: RawSource) {
                id = rawSource["id"]
                    ?.let(OrderDoc.id.getFieldType()::deserialize)
                statuses = rawSource["status"]
                    ?.let(RequiredListType(OrderDoc.status.getFieldType())::deserialize)
                total = rawSource["total"]
                    ?.let(OrderDoc.total.getFieldType()::deserialize)
                user = rawSource["user"]
                    ?.let(SourceType(OrderDoc.user.getFieldType(), ::User)::deserialize)
            }
        }

        val order = Order()
        order.fromSource(
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
            var id by OrderDoc.id.required()
        }

        class FullOrderDocSource : IdOrderDocSource() {
            var status by OrderDoc.status
            var total by OrderDoc.total
        }

        val minOrder = IdOrderDocSource()
        shouldThrow<IllegalStateException> {
            minOrder.toSource()
        }

        val fullOrder = FullOrderDocSource()
        shouldThrow<IllegalStateException> {
            fullOrder.toSource() shouldContainExactly emptyMap()
        }

        shouldThrow<IllegalStateException> {
            fullOrder.fromSource(emptySource())
        }

        fullOrder.fromSource(mapOf("id" to 1))
        fullOrder.id shouldBe 1
        fullOrder.status.shouldBeNull()
        fullOrder.total.shouldBeNull()
        fullOrder.toSource() shouldContainExactly mapOf("id" to 1L)

        fullOrder.fromSource(mapOf("id" to 2, "status" to 0))
        fullOrder.id shouldBe 2
        fullOrder.status shouldBe 0
        fullOrder.total.shouldBeNull()
        fullOrder.toSource() shouldContainExactly mapOf<String, Any?>("id" to 2L, "status" to 0)

        fullOrder.fromSource(mapOf("id" to 3, "total" to 9.99))
        fullOrder.id shouldBe 3
        fullOrder.status.shouldBeNull()
        fullOrder.total shouldBe 9.99F
        fullOrder.toSource() shouldContainExactly mapOf("id" to 3L, "total" to 9.99F)
    }

    @Test
    fun ignoreExtraFields() {
        class OrderDocSource : DocSource() {
            var status by OrderDoc.status
        }

        val order = OrderDocSource()
        order.fromSource(mapOf("status" to 0, "id" to 1))
        order.status shouldBe 0
        order.toSource() shouldContainExactly mapOf("status" to 0)
    }

    @Test
    fun optionalValue() {
        class OrderDocSource : DocSource() {
            var status by OrderDoc.status
        }

        val order = OrderDocSource()
        order.status.shouldBeNull()
        order.toSource() shouldContainExactly emptyMap()

        order.fromSource(mapOf("status" to null))
        order.status.shouldBeNull()
        order.toSource() shouldContainExactly mapOf("status"  to null)

        order.fromSource(mapOf("status" to 0))
        order.status shouldBe 0
        order.toSource() shouldContainExactly mapOf("status" to 0)

        order.status = 1
        order.status shouldBe 1
        order.toSource() shouldContainExactly mapOf("status" to 1)

        order.status = null
        order.status.shouldBeNull()
        order.toSource() shouldContainExactly mapOf("status" to null)
    }

    @Test
    fun requiredValue() {
        class OrderDocSource : DocSource() {
            var status by OrderDoc.status.required()
        }

        shouldThrow<IllegalStateException> {
            OrderDocSource().fromSource(emptySource())
        }

        shouldThrow<IllegalArgumentException> {
            OrderDocSource().fromSource(mapOf("status" to null))
        }

        val order = OrderDocSource()
        shouldThrow<IllegalStateException> {
            order.toSource()
        }

        order.fromSource(mapOf("status" to 0))
        order.status shouldBe 0
        order.toSource() shouldContainExactly mapOf("status" to 0)

        order.status = 1
        order.status shouldBe 1
        order.toSource() shouldContainExactly mapOf("status" to 1)
    }

    @Test
    fun optionalListOfOptionalValues() {
        class OrderDocSource : DocSource() {
            var status by OrderDoc.status.list()
        }

        val order = OrderDocSource()
        order.status.shouldBeNull()
        order.toSource() shouldContainExactly emptyMap()

        order.fromSource(mapOf("status" to null))
        order.status.shouldBeNull()
        order.toSource() shouldContainExactly mapOf("status" to null)

        order.fromSource(mapOf("status" to 1))
        order.status shouldBe listOf(1)
        order.toSource() shouldContainExactly mapOf("status" to listOf(1))

        order.status = emptyList()
        order.status shouldBe emptyList()
        order.toSource() shouldContainExactly mapOf("status" to emptyList<Nothing>())

        order.status = listOf(1, 2, null)
        order.status shouldBe listOf(1, 2, null)
        order.toSource() shouldContainExactly mapOf("status" to listOf(1, 2, null))

        order.fromSource(mapOf("status" to listOf(null)))
        order.status shouldBe listOf(null)
        order.toSource() shouldContainExactly mapOf("status" to listOf(null))
    }

    @Test
    fun optionalListOfRequiredValues() {
        class OrderDocSource : DocSource() {
            var status by OrderDoc.status.required().list()
        }

        val order = OrderDocSource()
        order.status.shouldBeNull()
        order.toSource() shouldContainExactly emptyMap()

        order.fromSource(mapOf("status" to null))
        order.status.shouldBeNull()
        order.toSource() shouldContainExactly mapOf("status" to null)

        order.status = emptyList()
        order.status shouldBe emptyList()
        order.toSource() shouldContainExactly mapOf("status" to emptyList<Nothing>())

        order.status = listOf(1, 2)
        order.status shouldBe listOf(1, 2)
        order.toSource() shouldContainExactly mapOf("status" to listOf(1, 2))

        shouldThrow<IllegalArgumentException> {
            order.fromSource(mapOf("status" to listOf(null)))
        }
        order.status shouldBe null
        order.toSource() shouldContainExactly emptyMap()
    }

    @Test
    fun requiredListOfOptionalValues() {
        class OrderDocSource : DocSource() {
            var status by OrderDoc.status.list().required()
        }

        val order = OrderDocSource()
        shouldThrow<IllegalStateException> {
            order.toSource()
        }

        shouldThrow<IllegalStateException> {
            order.status
        }

        shouldThrow<IllegalArgumentException> {
            order.fromSource(mapOf("status" to null))
        }
        shouldThrow<IllegalStateException> {
            order.toSource()
        }

        order.status = emptyList()
        order.status shouldBe emptyList()
        order.toSource() shouldBe mapOf("status" to emptyList<Nothing>())

        order.status = listOf(1, 2, null)
        order.status shouldBe listOf(1, 2, null)
        order.toSource() shouldBe mapOf("status" to listOf(1, 2, null))

        order.fromSource(mapOf("status" to listOf(null)))
        order.status shouldBe listOf(null)
        order.toSource() shouldBe mapOf("status" to listOf(null))
    }

    @Test
    fun requiredListOfRequiredValues() {
        class OrderDocSource : DocSource() {
            var status by OrderDoc.status.required().list().required()
        }

        val order = OrderDocSource()
        shouldThrow<IllegalStateException> {
            order.toSource()
        }

        shouldThrow<IllegalStateException> {
            order.status
        }

        shouldThrow<IllegalArgumentException> {
            order.fromSource(mapOf("status" to null))
        }
        shouldThrow<IllegalStateException> {
            order.toSource()
        }

        order.status = emptyList()
        order.status shouldBe emptyList()
        order.toSource() shouldBe mapOf("status" to emptyList<Nothing>())

        order.status = listOf(1, 2)
        order.status shouldBe listOf(1, 2)
        order.toSource() shouldBe mapOf("status" to listOf(1, 2))

        shouldThrow<IllegalArgumentException> {
            order.fromSource(mapOf("status" to listOf(null)))
        }
        shouldThrow<IllegalStateException> {
            order.toSource()
        }

        order.fromSource(mapOf("status" to listOf(2, 1)))
        order.status shouldBe listOf(2, 1)
        order.toSource() shouldBe mapOf("status" to listOf(2, 1))
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
        order.toSource() shouldBe emptyMap()

        order.fromSource(mapOf("user" to null))
        order.user.shouldBeNull()
        order.toSource() shouldBe mapOf("user" to null)

        order.fromSource(mapOf("user" to emptyMap<Nothing, Nothing>()))
        order.user.shouldNotBeNull().let { user ->
            user.id.shouldBeNull()
        }
        order.toSource() shouldBe mapOf("user" to emptyMap<Nothing, Nothing>())

        order.fromSource(mapOf("user" to mapOf("id" to 1)))
        order.user.shouldNotBeNull().let { user ->
            user.id shouldBe 1
        }
        order.toSource() shouldBe mapOf("user" to mapOf("id" to 1))

        order.user = null
        order.user.shouldBeNull()
        order.toSource() shouldBe mapOf("user" to null)

        order.user = UserDocSource().apply {
            id = 19
        }
        order.user?.id shouldBe 19
        order.toSource() shouldBe mapOf("user" to mapOf("id" to 19))
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
            order.toSource()
        }

        shouldThrow<IllegalArgumentException> {
            order.fromSource(mapOf("user" to null))
        }

        order.fromSource(mapOf("user" to emptyMap<Nothing, Nothing>()))
        order.user.shouldNotBeNull().let { user ->
            user.id.shouldBeNull()
        }
        order.toSource() shouldBe mapOf("user" to emptyMap<Nothing, Nothing>())

        order.fromSource(mapOf("user" to mapOf("id" to 1)))
        order.user.shouldNotBeNull().let { user ->
            user.id shouldBe 1
        }
        order.toSource() shouldContainExactly mapOf("user" to mapOf("id" to 1))

        order.user = UserDocSource().apply {
            id = 19
        }
        order.user.id shouldBe 19
        order.toSource() shouldContainExactly mapOf("user" to mapOf("id" to 19))
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
        order.toSource() shouldContainExactly emptyMap()

        order.fromSource(mapOf("user" to null))
        order.users.shouldBeNull()
        order.toSource() shouldContainExactly mapOf("user" to null)

        order.fromSource(mapOf("user" to emptyList<Nothing>()))
        order.users shouldBe emptyList()
        order.toSource() shouldContainExactly mapOf("user" to emptyList<Nothing>())

        order.fromSource(mapOf("user" to listOf(null)))
        order.users shouldBe listOf(null)
        order.toSource() shouldContainExactly mapOf("user" to listOf(null))

        order.fromSource(mapOf("user" to listOf(mapOf("id" to null))))
        order.users shouldBe listOf(UserDocSource(null))
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to null)))

        order.fromSource(mapOf("user" to listOf(mapOf("id" to 1))))
        order.users shouldBe listOf(UserDocSource(1))
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 1)))

        order.fromSource(mapOf("user" to mapOf("id" to 2)))
        order.users shouldBe listOf(UserDocSource(2))
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 2)))

        order.users = listOf(UserDocSource(id = 19))
        order.users.let { users ->
            users?.get(0)?.id shouldBe 19
        }
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 19)))
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
        order.toSource() shouldContainExactly emptyMap()

        order.fromSource(mapOf("user" to null))
        order.users.shouldBeNull()
        order.toSource() shouldContainExactly mapOf("user" to null)

        order.fromSource(mapOf("user" to emptyList<Nothing>()))
        order.users shouldBe emptyList()
        order.toSource() shouldContainExactly mapOf("user" to emptyList<Nothing>())

        shouldThrow<IllegalArgumentException> {
            order.fromSource(mapOf("user" to listOf(null)))
        }
        order.toSource() shouldContainExactly emptyMap()

        order.fromSource(mapOf("user" to listOf(mapOf("id" to null))))
        order.users shouldBe listOf(UserDocSource(null))
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to null)))

        order.fromSource(mapOf("user" to listOf(mapOf("id" to 1))))
        order.users shouldBe listOf(UserDocSource(1))
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 1)))

        order.fromSource(mapOf("user" to mapOf("id" to 2)))
        order.users shouldBe listOf(UserDocSource(2))
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 2)))

        order.users = listOf(UserDocSource(id = 19))
        order.users.let { users ->
            users?.get(0)?.id shouldBe 19
        }
        order.toSource() shouldBe mapOf("user" to listOf(mapOf("id" to 19)))
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
            order.toSource()
        }

        shouldThrow<IllegalArgumentException> {
            order.fromSource(mapOf("user" to null))
        }

        order.fromSource(mapOf("user" to emptyList<Nothing>()))
        order.users shouldBe emptyList()
        order.toSource() shouldContainExactly mapOf("user" to emptyList<Nothing>())

        order.fromSource(mapOf("user" to listOf(null)))
        order.users shouldBe listOf(null)
        order.toSource() shouldContainExactly mapOf("user" to listOf(null))

        order.fromSource(mapOf("user" to listOf(mapOf("id" to null))))
        order.users shouldBe listOf(UserDocSource(null))
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to null)))

        order.fromSource(mapOf("user" to listOf(mapOf("id" to 1))))
        order.users shouldBe listOf(UserDocSource(1))
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 1)))

        order.fromSource(mapOf("user" to mapOf("id" to 2)))
        order.users shouldBe listOf(UserDocSource(2))
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 2)))

        order.users = listOf(UserDocSource(id = 19))
        order.users.let { users ->
            users[0]?.id shouldBe 19
        }
        order.toSource() shouldContainExactly  mapOf("user" to listOf(mapOf("id" to 19)))
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
            order.toSource()
        }

        shouldThrow<IllegalArgumentException> {
            order.fromSource(mapOf("user" to null))
        }

        order.fromSource(mapOf("user" to emptyList<Nothing>()))
        order.users shouldBe emptyList()
        order.toSource() shouldContainExactly mapOf("user" to emptyList<Nothing>())

        shouldThrow<IllegalArgumentException> {
            order.fromSource(mapOf("user" to listOf(null)))
        }
        shouldThrow<IllegalStateException> {
            order.toSource()
        }

        order.fromSource(mapOf("user" to listOf(mapOf("id" to null))))
        order.users shouldBe listOf(UserDocSource(null))
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to null)))

        order.fromSource(mapOf("user" to listOf(mapOf("id" to 1))))
        order.users shouldBe listOf(UserDocSource(1))
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 1)))

        order.fromSource(mapOf("user" to mapOf("id" to 2)))
        order.users shouldBe listOf(UserDocSource(2))
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 2)))

        order.users = listOf(UserDocSource(id = 19))
        order.users.let { users ->
            users[0].id shouldBe 19
        }
        order.toSource() shouldContainExactly mapOf("user" to listOf(mapOf("id" to 19)))
    }

    @Test
    fun defaultValue() {
        class UserDocSource : DocSource() {
            var id by OrderDoc.user.id
        }

        class OrderDocSource : DocSource() {
            var id by OrderDoc.id.required()
            var status by OrderDoc.status.required().list().default { listOf(0, 3) }
            var total by OrderDoc.total.default { 666.66f }
            var user by OrderDoc.user.source(::UserDocSource)
                .default { UserDocSource().apply { id = 777 } }
        }

        val order = OrderDocSource()
        shouldThrow<IllegalStateException> {
            order.id
        }
        shouldThrow<IllegalStateException> {
            order.toSource()
        }

        order.id = 111L

        order.status shouldBe listOf(0, 3)
        order.total shouldBe 666.66f
        order.user shouldBe UserDocSource().apply { id = 777 }

        order.toSource() shouldContainExactly mapOf(
            "id" to 111L,
            "status" to listOf(0, 3),
            "total" to 666.66f,
            "user" to mapOf("id" to 777)
        )
    }
}
