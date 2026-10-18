package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestNormalizeSelectAnyFilter(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "NormalizeSelectAnyFilter")

	outer, outerCols := e.scan("t", 1)
	inner, innerCols := e.scan("u", 1)
	any := e.f.ConstructAny(inner, e.v(outerCols[0]), &memo.SubqueryPrivate{Cmp: opt.EqOp})
	before := e.f.ConstructSelect(outer, e.filters(any))

	eq := e.f.ConstructEq(e.v(outerCols[0]), e.v(innerCols[0]))
	correlated := e.f.ConstructSelect(inner, e.filters(eq))
	existsCol := e.f.Metadata().AddColumn("exists", types.Bool)
	exists := e.f.ConstructExists(correlated, &memo.ExistsPrivate{LazyEvalProjectionCol: existsCol})
	want := e.f.ConstructSelect(outer, e.filters(exists))
	e.expectRewrite(rule, before, want)

	outer2, outerCols2 := e.scan("t", 1)
	inner2, _ := e.scan("u", 1)
	any2 := e.f.ConstructAny(inner2, e.v(outerCols2[0]), &memo.SubqueryPrivate{Cmp: opt.EqOp})
	before2 := e.f.ConstructSelect(outer2, e.filters(any2, e.f.ConstructEq(e.v(outerCols2[0]), e.num(7))))
	e.expectNoMatch(rule, before2)
}
