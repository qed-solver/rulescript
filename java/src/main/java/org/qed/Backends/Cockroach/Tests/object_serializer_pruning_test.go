package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestObjectSerializerPruning(t *testing.T) {
	rule := loadRule(t, "ObjectSerializerPruning")
	build := func(e *env, serializerOrdinals, outputOrdinals []int) memo.RelExpr {
		source, sourceCols := e.scan("t", 1)
		items := make(memo.ProjectionsExpr, len(serializerOrdinals))
		serializerCols := make([]opt.ColumnID, 3)
		for i := range serializerCols {
			serializerCols[i] = e.f.Metadata().AddColumn("serializer", types.Bool)
		}
		for i, ordinal := range serializerOrdinals {
			items[i] = e.f.ConstructProjectionsItem(e.f.ConstructEq(e.v(sourceCols[0]), e.num(ordinal)), serializerCols[ordinal])
		}
		serialized := e.f.ConstructProject(source, items, opt.ColSet{})
		output := opt.ColSet{}
		for _, ordinal := range outputOrdinals {
			output.Add(serializerCols[ordinal])
		}
		return e.f.ConstructProject(serialized, memo.ProjectionsExpr{}, output)
	}

	e := newEnv(t)
	before := build(e, []int{0, 1, 2}, []int{0, 2})
	want := build(newEnv(t), []int{0, 2}, []int{0, 2})
	e.expectRewrite(rule, before, want)

	nearMiss := build(newEnv(t), []int{0, 1, 2}, []int{0, 1})
	e.expectNoMatch(rule, nearMiss)
}
