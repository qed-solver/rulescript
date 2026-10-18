package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestExtractPythonUDFFromJoinCondition(t *testing.T) {
	rule := loadRule(t, "ExtractPythonUDFFromJoinCondition")
	e := newEnv(t)
	l, lc := e.scan("u", 2)
	r, _ := e.scan("t", 3)
	udf := e.f.ConstructGt(e.v(lc[0]), e.num(5))
	rest := e.f.ConstructEq(e.v(lc[1]), e.num(3))
	on := e.filters(e.f.ConstructAnd(udf, rest))
	before := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	want := e.f.ConstructSelect(
		e.f.ConstructInnerJoin(l, r, e.filters(rest), &memo.JoinPrivate{}),
		e.filters(udf),
	)
	e.expectRewrite(rule, before, want)
	e.expectRewrite(rule, e.f.ConstructInnerJoin(l, r, e.filters(udf, rest), &memo.JoinPrivate{}), want)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSelect(before, e.filters(udf)))
}
