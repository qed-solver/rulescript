package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestInlineConstVar(t *testing.T) {
	rule := loadRule(t, "InlineConstVar")
	e := newEnv(t)
	src, sc := e.scan("t", 1)
	cst, cc := e.scan("u", 1)
	on := e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(cc[0])))
	f := func(col opt.ColumnID) opt.ScalarExpr {
		return e.f.ConstructGt(e.v(col), e.num(2))
	}
	join := e.f.ConstructInnerJoin(src, cst, on, &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(f(sc[0])))
	want := e.f.ConstructSelect(join, e.filters(f(cc[0])))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, join)
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(f(cc[0]))))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructLeftJoin(src, cst, on, &memo.JoinPrivate{}), e.filters(f(sc[0]))))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructInnerJoin(src, cst, e.filters(e.f.ConstructLt(e.v(sc[0]), e.v(cc[0]))), &memo.JoinPrivate{}),
		e.filters(f(sc[0]))))
	wide, _ := e.scan("u", 2)
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructInnerJoin(src, wide, on, &memo.JoinPrivate{}), e.filters(f(sc[0]))))
}
