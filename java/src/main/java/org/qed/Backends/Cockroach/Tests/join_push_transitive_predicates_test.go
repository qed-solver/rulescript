package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinPushTransitivePredicates(t *testing.T) {
	rule := loadRule(t, "JoinPushTransitivePredicates")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	predRight := e.f.ConstructEq(e.v(rc[0]), e.num(4))
	predLeft := e.f.ConstructEq(e.v(lc[0]), e.num(4))
	right := e.f.ConstructSelect(r, e.filters(predRight))
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructInnerJoin(l, right, on, &memo.JoinPrivate{})
	filteredLeft := e.f.ConstructSelect(l, e.filters(predLeft))
	want := e.f.ConstructInnerJoin(filteredLeft, right, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	l2, lc2 := e.scan("u", 1)
	r2, rc2 := e.scan("t", 1)
	otherPredicate := e.f.ConstructEq(e.v(rc2[0]), e.num(4))
	right2 := e.f.ConstructSelect(r2, e.filters(otherPredicate))
	on2 := e.filters(e.f.ConstructEq(e.v(lc2[0]), e.v(rc2[0])))
	leftFilter := e.f.ConstructSelect(l2, e.filters(e.f.ConstructEq(e.v(lc2[0]), e.num(4))))
	e.expectRewrite(rule, e.f.ConstructInnerJoin(leftFilter, right2, on2, &memo.JoinPrivate{}),
		e.f.ConstructInnerJoin(e.f.ConstructSelect(leftFilter, e.filters(e.f.ConstructEq(e.v(lc2[0]), e.num(4)))), right2, on2, &memo.JoinPrivate{}))

	l3, lc3 := e.scan("u", 1)
	r3, rc3 := e.scan("t", 1)
	unfilteredRight := e.f.ConstructInnerJoin(l3, r3, e.filters(e.f.ConstructEq(e.v(lc3[0]), e.v(rc3[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, unfilteredRight)

	l4, lc4 := e.scan("u", 1)
	r4, rc4 := e.scan("t", 1)
	filteredRight := e.f.ConstructSelect(r4, e.filters(e.f.ConstructEq(e.v(rc4[0]), e.num(4))))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l4, filteredRight, e.filters(e.f.ConstructEq(e.v(lc4[0]), e.v(rc4[0]))), &memo.JoinPrivate{}))
}
