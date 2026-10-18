package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFilterFlattenCorrelatedCondition(t *testing.T) {
	rule := loadRule(t, "FilterFlattenCorrelatedCondition")
	e := newEnv(t)
	outer, oc := e.scan("u", 1)
	inner, ic := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(outer, inner, nil, &memo.JoinPrivate{})
	expr := e.f.ConstructPlus(e.v(ic[0]), e.num(1))
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructLt(e.v(oc[0]), expr)))

	exprCol := e.f.Metadata().AddColumn("expr", types.Int)
	passthrough := opt.MakeColSet(oc[0], ic[0])
	projected := e.f.ConstructProject(join, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(expr, exprCol)}, passthrough)
	filtered := e.f.ConstructSelect(projected, e.filters(e.f.ConstructLt(e.v(oc[0]), e.v(exprCol))))
	want := e.f.ConstructProject(filtered, memo.EmptyProjectionsExpr, passthrough)
	e.expectRewriteUpToFreshColumns(rule, before, want)

	leftJoin := e.f.ConstructLeftJoin(outer, inner, nil, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(leftJoin, e.filters(e.f.ConstructLt(e.v(oc[0]), expr))))
}
