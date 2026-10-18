package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectToCalc(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectToCalc")
	input, cols := e.scan("u", 2)
	first := e.f.ConstructProjectionsItem(e.f.ConstructEq(e.v(cols[0]), e.num(1)), e.f.Metadata().AddColumn("first", types.Bool))
	second := e.f.ConstructProjectionsItem(e.f.ConstructEq(e.v(cols[1]), e.num(2)), e.f.Metadata().AddColumn("second", types.Bool))
	projections := memo.ProjectionsExpr{first, second}
	before := e.f.ConstructProject(input, projections, opt.ColSet{})
	e.expectAbsorbed(rule, before)

	nearMiss := e.f.ConstructProject(input, memo.ProjectionsExpr{first}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}
