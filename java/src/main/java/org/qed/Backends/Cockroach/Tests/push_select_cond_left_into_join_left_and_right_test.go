package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushSelectCondLeftIntoJoinLeftAndRight(t *testing.T) {
	rule := loadRule(t, "PushSelectCondLeftIntoJoinLeftAndRight")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	join := e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(1))))
	filteredLeft := e.f.ConstructSelect(l, e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(1))))
	filteredRight := e.f.ConstructSelect(r, e.filters(e.f.ConstructEq(e.v(rc[0]), e.num(1))))
	want := e.f.ConstructLeftJoin(filteredLeft, filteredRight, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	inner := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(inner, e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(1)))))
	wide, wc := e.scan("u", 2)
	nullable := e.f.ConstructProject(wide, nil, opt.MakeColSet(wc[1]))
	nullJoin := e.f.ConstructLeftJoin(nullable, r, e.filters(e.f.ConstructEq(e.v(wc[1]), e.v(rc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(nullJoin, e.filters(e.f.ConstructEq(e.v(wc[1]), e.num(1)))))
}

func TestSQLPushSelectCondLeftIntoJoinLeftAndRight(t *testing.T) {
	expectSQLRewrites(t, "PushSelectCondLeftIntoJoinLeftAndRight", "SELECT l.a FROM (VALUES (1), (2)) AS l(a) LEFT JOIN (VALUES (1), (2)) AS r(b) ON l.a = r.b WHERE l.a > 0")
}
