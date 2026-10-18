package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushVariantIntoScan(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushVariantIntoScan")
	scan, cols := e.scan("nn", 2)
	a := e.f.Metadata().AddColumn("a", types.Int)
	b := e.f.Metadata().AddColumn("b", types.Int)
	extractA := func() opt.ScalarExpr { return e.f.ConstructPlus(e.v(cols[1]), e.num(1)) }
	extractB := func() opt.ScalarExpr { return e.f.ConstructPlus(e.v(cols[1]), e.num(2)) }
	projections := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(extractA(), a),
			e.f.ConstructProjectionsItem(extractB(), b),
		}
	}
	filtered := e.f.ConstructSelect(scan, e.filters(e.f.ConstructEq(extractA(), e.num(5))))
	before := e.f.ConstructProject(filtered, projections(), opt.MakeColSet(cols[0], cols[1]))

	structScan := e.f.ConstructProject(scan, projections(), opt.MakeColSet(cols[0], cols[1]))
	filteredStruct := e.f.ConstructSelect(structScan, e.filters(e.f.ConstructEq(e.v(a), e.num(5))))
	want := e.f.ConstructProject(filteredStruct, nil, opt.MakeColSet(cols[0], cols[1], a, b))
	e.expectRewrite(rule, before, want)

	onPassthrough := e.f.ConstructSelect(scan, e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(5))))
	nearMiss := e.f.ConstructProject(onPassthrough, projections(), opt.MakeColSet(cols[0], cols[1]))
	e.expectNoMatch(rule, nearMiss)
}
