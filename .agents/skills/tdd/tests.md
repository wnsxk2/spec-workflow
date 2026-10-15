# 좋은 테스트와 나쁜 테스트

## 좋은 테스트

**통합 스타일**: 내부 부품의 목(mock)이 아니라 실제 인터페이스를 통해 테스트한다.

```typescript
// 좋음: 관찰 가능한 동작을 테스트한다
test("user can checkout with valid cart", async () => {
  const cart = createCart();
  cart.add(product);
  const result = await checkout(cart, paymentMethod);
  expect(result.status).toBe("confirmed");
});
```

특징:

- 사용자/호출자가 신경 쓰는 동작을 테스트한다
- 공개 API만 사용한다
- 내부 리팩터링에도 살아남는다
- 어떻게(HOW)가 아니라 무엇을(WHAT) 서술한다
- 테스트 하나에 논리적 단언 하나

## 나쁜 테스트

**구현 세부사항 테스트**: 내부 구조에 결합되어 있다.

```typescript
// 나쁨: 구현 세부사항을 테스트한다
test("checkout calls paymentService.process", async () => {
  const mockPayment = jest.mock(paymentService);
  await checkout(cart, payment);
  expect(mockPayment.process).toHaveBeenCalledWith(cart.total);
});
```

위험 신호:

- 내부 협력 객체를 모킹함
- private 메서드를 테스트함
- 호출 횟수/순서를 단언함
- 동작 변화 없이 리팩터링만 해도 테스트가 깨짐
- 테스트 이름이 무엇이 아니라 어떻게를 서술함
- 인터페이스 대신 외부 수단으로 검증함

```typescript
// 나쁨: 인터페이스를 우회해 검증한다
test("createUser saves to database", async () => {
  await createUser({ name: "Alice" });
  const row = await db.query("SELECT * FROM users WHERE name = ?", ["Alice"]);
  expect(row).toBeDefined();
});

// 좋음: 인터페이스를 통해 검증한다
test("createUser makes user retrievable", async () => {
  const user = await createUser({ name: "Alice" });
  const retrieved = await getUser(user.id);
  expect(retrieved.name).toBe("Alice");
});
```

**동어반복적 테스트**: 기댓값이 구현을 그대로 되풀이하므로 구조상 항상 통과한다.

```typescript
// 나쁨: 코드가 계산하는 방식 그대로 기댓값을 다시 계산한다
test("calculateTotal sums line items", () => {
  const items = [{ price: 10 }, { price: 5 }];
  const expected = items.reduce((sum, i) => sum + i.price, 0);
  expect(calculateTotal(items)).toBe(expected);
});

// 좋음: 기댓값이 독립적이고 알려진 리터럴이다
test("calculateTotal sums line items", () => {
  expect(calculateTotal([{ price: 10 }, { price: 5 }])).toBe(15);
});
```
