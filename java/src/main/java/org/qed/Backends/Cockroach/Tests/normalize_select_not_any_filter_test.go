package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestNormalizeSelectNotAnyFilter(t *testing.T) {
	rule := loadRule(t, "NormalizeSelectNotAnyFilter")
	e := newEnv(t)
	outer, outerCols := e.scan("t", 1)
	sub, _ := e.scan("u", 1)
	any := e.f.ConstructAny(sub, e.v(outerCols[0]), &memo.SubqueryPrivate{Cmp: opt.EqOp})
	before := e.f.ConstructSelect(outer, e.filters(e.f.ConstructNot(any)))

	correlatedEq := e.f.ConstructEq(e.v(outerCols[0]), e.v(6))
	correlatedNull := e.f.ConstructIs(correlatedEq, memo.NullSingleton)
	inside := e.f.ConstructSelect(sub, e.filters(e.f.ConstructOr(correlatedEq, correlatedNull)))
	exists := e.f.ConstructExists(inside, &memo.ExistsPrivate{LazyEvalProjectionCol: e.f.Metadata().AddColumn("exists", types.Bool)})
	want := e.f.ConstructSelect(outer, e.filters(e.f.ConstructNot(exists)))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	other, otherCols := e.scan("t", 1)
	otherSub, _ := e.scan("u", 1)
	otherAny := e.f.ConstructAny(otherSub, e.v(otherCols[0]), &memo.SubqueryPrivate{Cmp: opt.EqOp})
	nearMiss := e.f.ConstructSelect(other, e.filters(
		e.f.ConstructNot(otherAny),
		e.f.ConstructEq(e.v(otherCols[0]), e.num(1)),
	))
	e.expectNoMatch(rule, nearMiss)
}
