package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPullOutNestedDataOuterRefExpressions(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PullOutNestedDataOuterRefExpressions")
	outer, outerCols := e.scan("t", 1)
	inner, innerCols := e.scan("u", 1)
	base := e.f.ConstructInnerJoin(outer, inner, memo.TrueFilter, memo.EmptyJoinPrivate)
	outerValue := e.v(outerCols[0])
	innerValue := e.v(innerCols[0])
	nestedValue := e.f.ConstructPlus(outerValue, e.num(1))
	before := e.f.ConstructSelect(base, e.filters(e.f.ConstructEq(nestedValue, innerValue)))
	computedCol := e.f.Metadata().AddColumn("nested", nestedValue.DataType())
	projected := e.f.ConstructProject(base, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(nestedValue, computedCol),
	}, opt.MakeColSet(outerCols[0], innerCols[0]))
	filtered := e.f.ConstructSelect(projected, e.filters(e.f.ConstructEq(e.v(computedCol), innerValue)))
	want := e.f.ConstructProject(filtered, memo.EmptyProjectionsExpr, opt.MakeColSet(outerCols[0], innerCols[0]))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearMiss := e.f.ConstructLeftJoin(outer, inner, memo.TrueFilter, memo.EmptyJoinPrivate)
	nearMiss = e.f.ConstructSelect(nearMiss, e.filters(e.f.ConstructEq(nestedValue, innerValue)))
	e.expectNoMatch(rule, nearMiss)
}
