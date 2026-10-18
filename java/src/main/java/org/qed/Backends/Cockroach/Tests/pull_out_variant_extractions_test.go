package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPullOutVariantExtractions(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PullOutVariantExtractions")
	input, cols := e.scan("t", 2)
	extracted := e.f.Metadata().AddColumn("extracted", types.Int)
	total := e.f.Metadata().AddColumn("total", types.Int)
	project := e.f.ConstructProject(input,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[1]), e.num(1)), extracted)},
		opt.MakeColSet(cols[0]),
	)
	before := e.f.ConstructGroupBy(project,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(extracted)), total)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])},
	)
	e.expectAbsorbed(rule, before)

	other, otherCols := e.scan("t", 2)
	bad := e.f.ConstructGroupBy(other,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(otherCols[1])), total)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(otherCols[0])},
	)
	e.expectNoMatch(rule, bad)
}
