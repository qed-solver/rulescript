package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFoldComparisonWithAny(t *testing.T) {
	rule := loadRule(t, "FoldComparisonWithAny")
	e := newEnv(t)
	trueLit := func() opt.ScalarExpr { return memo.TrueSingleton }
	falseLit := func() opt.ScalarExpr { return memo.FalseSingleton }
	eq := func(a, b opt.ScalarExpr) opt.ScalarExpr { return e.f.ConstructEq(a, b) }
	src, _ := e.scan("t", 3)
	cond := e.f.ConstructOr(eq(trueLit(), falseLit()), eq(trueLit(), trueLit()))
	before := e.f.ConstructSelect(src, e.filters(cond))
	want := src
	e.expectRewrite(rule, before, want)
	nearMiss := e.f.ConstructOr(eq(trueLit(), falseLit()), eq(trueLit(), falseLit()))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(nearMiss)))
}
