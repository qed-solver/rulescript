package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateZeroCardProject(t *testing.T) {
	rule := loadRule(t, "EliminateZeroCardProject")
	e := newEnv(t)
	c0 := e.f.Metadata().AddColumn("c0", types.Int)
	c1 := e.f.Metadata().AddColumn("c1", types.Int)
	f := e.f.Metadata().AddColumn("f", types.Int)
	passthrough := opt.MakeColSet(c0, c1)
	projections := memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(c0)), f)}

	input := e.empty(c0, c1)
	before := e.f.ConstructProject(input, projections, passthrough)
	e.expectRewrite(rule, before, e.empty(c0, c1, f))

	nonEmpty := e.intValues([]opt.ColumnID{c0, c1}, []int{1, 2})
	e.expectNoMatch(rule, e.f.ConstructProject(nonEmpty, projections, passthrough))

	e.expectNoMatch(rule, e.f.ConstructProject(input, projections, opt.MakeColSet(c0)))

	overProjection := memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(c1)), f)}
	e.expectNoMatch(rule, e.f.ConstructProject(input, overProjection, passthrough))

	c2 := e.f.Metadata().AddColumn("c2", types.Int)
	wideInput := e.empty(c0, c1, c2)
	e.expectNoMatch(rule, e.f.ConstructProject(wideInput, projections, passthrough))
}
