package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestInlineJoinConstantsLeft(t *testing.T) {
	rule := loadRule(t, "InlineJoinConstantsLeft")
	e := newEnv(t)
	src, sc := e.scan("t", 1)
	cst, cc := e.scan("u", 1)
	r, rc := e.scan("nn", 1)
	constCond := e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(cc[0])))
	left := e.f.ConstructInnerJoin(src, cst, constCond, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(left, r, e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(left, r, e.filters(e.f.ConstructEq(e.v(cc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	outer := e.f.ConstructLeftJoin(left, r, e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, outer)

	leftOuter := e.f.ConstructLeftJoin(src, cst, constCond, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(leftOuter, r, e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(rc[0]))), &memo.JoinPrivate{}))
}
